package com.vonage.nonhmac.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.vonage.db.PostgresConnector;
import com.vonage.kibana_crawler.blocking_queues.BlockingQueueFactory;
import com.vonage.kibana_crawler.builder.AppCustomizedKibanaRequestBuilder;
import com.vonage.kibana_crawler.pojo.AppCustomizedKibanaRequest;
import com.vonage.kibana_crawler.pojo.KibanaRequestHeader;
import com.vonage.kibana_crawler.pojo.kibana_request.KibanaRequest;
import com.vonage.kibana_crawler.pojo.kibana_response.KibanaResponse;
import com.vonage.kibana_crawler.resource.KibanaAPIResource;
import com.vonage.kibana_crawler.service.FileService;
import com.vonage.kibana_crawler.service.kibana_service.IKibanaAPIService;
import com.vonage.kibana_crawler.utilities.DateBatchProducer;
import com.vonage.kibana_crawler.utilities.Helpers;
import com.vonage.kibana_crawler.utilities.KibanaResponseHelper;
import com.vonage.kibana_crawler.utilities.constants.CrawlerConstants;
import com.vonage.kibana_crawler.utilities.constants.FileTypes;
import com.vonage.nonhmac.pojo.NonHmacData;
import com.vonage.nonhmac.pojo.RequestIDData;
import com.vonage.nonhmac.pojo.User;
import com.vonage.nonhmac.pojo.UserAuthView;
import com.vonage.nonhmac.repos.RequestIDDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NonHmacDataService {

    private final IKibanaAPIService kibanaAPIService;

    private final BlockingQueueFactory<KibanaResponse> blockingQueueFactory;

    private final RequestIDDataRepository requestIDDataRepository;

    private final FileService fileService;

    private final PostgresConnector pgConnector;

    private final String userdataPayloadPath = "/json/UserInfoPayload.json";

    private final String ACCOUNT_NAME_FETCHING_QUERY = "select ad.account_id, ad.account_name from customer.account_data as ad where ad.account_id in (<ACCOUNT_IDS>);";

    private LocalDateTime minStartTime = null;

    private enum ProcessingTerminationReason{
        NO_DATA,
        UNAUTHORIZED
    }

    private int fileCount = 41;

    public void getData(LocalDateTime start, LocalDateTime end){
        ExecutorService fetchResponseThreadPool = Executors.newFixedThreadPool(10);
        ExecutorService responseProcessorThreadPool = Executors.newFixedThreadPool(10);
        DateBatchProducer dateBatchProducer = new DateBatchProducer(start, end, TimeUnit.MINUTES, 12);
        while(dateBatchProducer.hasNext()){
            final Pair<LocalDateTime, LocalDateTime> batch = dateBatchProducer.next();
            AppCustomizedKibanaRequest request = createRequest(batch.getLeft(), batch.getRight());
            try(BlockingQueueFactory<KibanaResponse>.CrawlerBlockingQueue blockingQueue = blockingQueueFactory.getBlockingQueue()) {
                responseProcessorThreadPool.submit(() -> {
                    ProcessingTerminationReason reason = responseProcessor(blockingQueue.getQueue());
                    if(ProcessingTerminationReason.UNAUTHORIZED.equals(reason)){
                        minStartTime = (Objects.isNull(minStartTime) || batch.getLeft().isBefore(minStartTime)) ? batch.getLeft() : minStartTime;
                        responseProcessorThreadPool.shutdown();
                        fetchResponseThreadPool.shutdownNow();
                    }
                });
                fetchResponseThreadPool.submit(() -> kibanaAPIService.sendRequest(request, blockingQueue.getQueue()));
            } catch (InterruptedException e) {
                log.error("Failed to send request to Kibana API", e);
            }
        }
        Helpers.shutdown(fetchResponseThreadPool, 2, TimeUnit.HOURS);
        Helpers.shutdown(responseProcessorThreadPool, 2, TimeUnit.HOURS);
    }

    /*
    com.vocalocity.hdap.click2callme.Click2CallMeHelper
    com.vocalocity.hdap.security.SignatureAuthenticationFilter
    com.vocalocity.hdap.rest.app.VocalocityRestGuard
     */
    private void getUserdataFromRequestIDs(LocalDateTime start, LocalDateTime end){
        final int pageSize = 10;
        int currentPage = 0;
        final ExecutorService getUserdataPool = Executors.newFixedThreadPool(10);
        final long dataSize = requestIDDataRepository.count();
        long totalPages = dataSize / pageSize;
        while(totalPages > 0){
            int _currentPage = currentPage;
            getUserdataPool.submit(() -> {
                final List<NonHmacData> nonHmacData = new ArrayList<>();
                List<RequestIDData> requestIDData = requestIDDataRepository.findAll(PageRequest.of(_currentPage, pageSize)).toList();
                Map<String, RequestIDData> requestIDDataMap = new HashMap<>();
                for(RequestIDData requestIDDataItem : requestIDData){
                    requestIDDataMap.put(requestIDDataItem.getRequestId(), requestIDDataItem);
                }
                List<String> requestIDs = requestIDData.stream().map(RequestIDData::getRequestId).collect(Collectors.toList());
                KibanaResponse response = kibanaAPIService.sendRequest(KibanaRequest.fromJson(getUserdataPayload(requestIDs, start, end)));
                KibanaResponseHelper.getMessage(response).stream().distinct().forEach(message -> {
                    String requestId = getRequestId(message);
                    if(requestIDDataMap.containsKey(requestId)){
                        nonHmacData.add(new NonHmacData(null, createUserData(message), requestIDDataMap.get(requestId)));
                        requestIDDataMap.remove(requestId);
                    }
                }
                );
                requestIDDataMap.forEach((reqId, data) -> {
                    if(data.getAuthType().equals("skip")){
                        nonHmacData.add(new NonHmacData(null, User.anonymous(), data));
                    }
                    else nonHmacData.add(new NonHmacData(null, User.unknown(), data));
                });
                fileService.writeResult(new File("Batch" + fileCount + FileTypes.CSV.getExtension()), false,nonHmacData.stream().map(NonHmacData::toString).collect(Collectors.joining("\n")));
            });
            totalPages--;
            currentPage++;
        }
        Helpers.shutdown(getUserdataPool, 20, TimeUnit.MINUTES);
    }

    private Map<String, String> fetchAccountName(List<String> accountIds){
        Function<ResultSet, Map<String, String>> resultProcessor = resultSet -> {
            Map<String, String> result = new HashMap<>();
            try{
                while(resultSet.next()){
                    result.putIfAbsent(resultSet.getString("account_id"), resultSet.getString("account_name"));
                }
            } catch (Exception e){
                log.error("Failed to fetch account name", e);
            }
            return result;
        };
        return pgConnector.execute(ACCOUNT_NAME_FETCHING_QUERY.replace("<ACCOUNT_IDS>", String.join(", ", accountIds)), resultProcessor);
    }

    private String getRequestId(String message){
        int bracketCount = 2;
        int i = 0;
        for(; i < message.length(); i++){
            if(message.charAt(i) == '['){
                bracketCount--;
            }
            if(bracketCount == 0){
                break;
            }
        }
        i++;
        StringBuilder requestIdBuilder = new StringBuilder();
        while(message.charAt(i) != ']'){
            requestIdBuilder.append(message.charAt(i));
            i++;
        }
        return requestIdBuilder.toString();
    }

    private User createUserData(String message){
        if(message.contains("\\[com.vocalocity.hdap.click2callme.Click2CallMeHelper]")){
            String[] split = message.split("\\[com.vocalocity.hdap.click2callme.Click2CallMeHelper]");
            if(split.length == 2){
                User user = User.unknown();
                user.setUserId(split[1].split(":")[1].trim());
                return user;
            }
        }
        if(message.contains("VBCC-1700")){
            String[] split = message.split("\\[VBCC-1700]");
            if(split.length == 2){
                String[] userInfo = split[1].split(", ");
                User user = new User();
                if(userInfo.length > 0){
                    user.setUsername(getAfterColon(userInfo[0]));
                }
                if(userInfo.length > 1){
                    user.setAccountId(getAfterColon(userInfo[1]));
                }
                if(userInfo.length > 2){
                    user.setUserId(getAfterColon(userInfo[2]));
                }
                return user;
            }
        }
        return User.unknown();
    }

    private String getAfterColon(String str){
        String[] split = str.split(":");
        if(split.length == 2){
            return split[1].trim();
        }
        return str;
    }

    private String getUserdataPayload(List<String> requestIds, LocalDateTime start, LocalDateTime end){
        int maxReqExpected = 10;
        try {
            String userdataPayload = new String(Files.readAllBytes(Paths.get(this.getClass().getResource(userdataPayloadPath).toURI())));
            for(int i=0; i<maxReqExpected; i++){
                userdataPayload = userdataPayload.replaceFirst("<REQID>", requestIds.get(i));
            }
            userdataPayload = userdataPayload.replaceFirst("<GTE>", start.toString());
            userdataPayload = userdataPayload.replaceFirst("<LTE>", end.toString());
            return userdataPayload;
        } catch (Exception e) {
            log.error("Error while building userdata payload.", e);
        }
        return "";
    }

    private AppCustomizedKibanaRequest createRequest(LocalDateTime start, LocalDateTime end){
        return new AppCustomizedKibanaRequestBuilder()
                .addMustNot(new MutablePair<>("account", "QA"))
                .addMustNot(new MutablePair<>("message", "hmac-signature"))
                .addMatchPhrase(new MutablePair<>("message", "\"status_code\":200"))
                .addMustNot(new MutablePair<>("message", "/appserver/version.properties"))
                .index("vbc_platform_services_team*")
                .size(10000)
                .addMultiMatch("uri")
                .addRange("timestamp", start.toString(), end.toString())
                .build();
    }

    private ProcessingTerminationReason responseProcessor(BlockingQueue<KibanaResponse> container) {
        while (true) {
            KibanaResponse response;
            try {
                response = container.poll(30, TimeUnit.MINUTES);
                if (KibanaResponse.invalidResponse().equals(response)) {
                    break;
                }
                if(KibanaResponse.unauthorizedResponse().equals(response)){
                    return ProcessingTerminationReason.UNAUTHORIZED;
                }
                if(Objects.nonNull(response)){
                    List<RequestIDData> requestIdData = KibanaResponseHelper.getMessage(response).stream()
                            .map(message -> message.split("\\[com.vocalocity.hdap.logging.RequestLogFilter]"))
                            .filter(split -> split.length == 2)
                            .map(split -> split[1].trim())
                            .map(jsonLog -> {
                                try {
                                    return CrawlerConstants.MAPPER.readValue(jsonLog, new TypeReference<Map<String, String>>() {
                                    });
                                } catch (JsonProcessingException e) {
                                    return new HashMap<String, String>();
                                }
                            })
                            .map(map -> {
                                RequestIDData requestIDData = new RequestIDData();
                                requestIDData.setRequestId(map.getOrDefault("request_id", ""));
                                requestIDData.setAuthType(map.getOrDefault("auth_type", ""));
                                requestIDData.setUri(map.getOrDefault("uri", ""));
                                return requestIDData;
                            })
                            .collect(Collectors.toList());
                    requestIDDataRepository.saveAll(requestIdData);
                }
            } catch (Exception e) {
                log.error("Error while getting response from queue", e);
            }
        }
        return ProcessingTerminationReason.NO_DATA;
    }

    @Async
    public Future<LocalDateTime> startCollection(Pair<LocalDateTime, LocalDateTime> duration){
        DateBatchProducer dateBatchProducer = new DateBatchProducer(duration.getLeft(), duration.getRight(), TimeUnit.HOURS, 2);
        while(dateBatchProducer.hasNext()){
            log.info("Batch {} started.", fileCount);
            Pair<LocalDateTime, LocalDateTime> dateBatch = dateBatchProducer.next();
            LocalDateTime _start = dateBatch.getLeft();
            LocalDateTime _end = dateBatch.getRight();
            getData(_start, _end);
            if(Objects.nonNull(minStartTime)) break;
            getUserdataFromRequestIDs(_start, _end);
            requestIDDataRepository.deleteAll();
            log.info("Batch {} completed", fileCount);
            fileCount++;
        }
        return CompletableFuture.completedFuture(minStartTime);
    }

    public void combineResult() throws InterruptedException, FileNotFoundException {
        List<UserAuthView> userAuthViews = new ArrayList<>();
        File dir = new File(".");
        for(File file: dir.listFiles()){
            if(file.getName().endsWith(".csv") && file.getName().startsWith("Batch")){
                Thread t = new Thread(() -> readFile(file, userAuthViews));
                t.start();
                t.join();
                readFile(file, userAuthViews);
            }
        }

        File externalUriCsv = new File("ext_api_calls_with_count_oct_month (1).csv");
        Scanner scanner = new Scanner(externalUriCsv);
        List<String> uris = new ArrayList<>();
        while(scanner.hasNextLine()){
            String[] split = scanner.nextLine().split(",");
            uris.add(split[1].substring(1, split[1].length() - 1).trim());
        }

        File uniqueDataInternal = new File("UniqueDataInternal.csv");
        File uniqueDataExternal = new File("UniqueDataExternal.csv");
        log.info("Final size {}", userAuthViews.size());
        StringBuilder builderA = new StringBuilder();
        StringBuilder builderB = new StringBuilder();

        Map<UserAuthView, List<String>> externalAuthViews = new HashMap<>();
        Map<UserAuthView, List<String>> internalAuthViews = new HashMap<>();
        for(UserAuthView authView: userAuthViews){
            if(isExternal(authView, uris)){
                externalAuthViews.computeIfAbsent(authView, k -> new ArrayList<>()).add(authView.getUri());
            }
            else {
//                log.info(authView.toString());
                internalAuthViews.computeIfAbsent(authView, k -> new ArrayList<>()).add(authView.getUri());
            }
        }

        populateAccountNames(internalAuthViews.keySet());
        populateAccountNames(externalAuthViews.keySet());

        internalAuthViews.forEach((k, v) -> builderA.append(k.getUser()).append(", ").append(k.getAccountName()).append("\n"));
        externalAuthViews.forEach((k, v) -> builderB.append(k.getUser()).append(", ").append(k.getAccountName()).append("\n"));


        fileService.writeResult(uniqueDataInternal, true, builderA.toString());
        fileService.writeResult(uniqueDataExternal, true, builderB.toString());
    }

    private void populateAccountNames(Set<UserAuthView> userAuthViews){
        Map<String, String> accountNames = fetchAccountName(
                userAuthViews.stream()
                        .map(userAuthView -> userAuthView.getUser().getAccountId())
                        .filter(accountId -> !accountId.equalsIgnoreCase("UNKNOWN"))
                        .collect(Collectors.toList()));
        for(UserAuthView userAuthView: userAuthViews){
            String accountName = accountNames.get(userAuthView.getUser().getAccountId());
//            log.info(accountName);
            userAuthView.setAccountName(accountName);
        }
    }

    private boolean match(String uri, String external, int i, int j){
        if(i == uri.length()){
            return j == external.length();
        }
        if(j == external.length()) return false;
        if(external.charAt(j) == uri.charAt(i)){
            return match(uri, external, i+1, j+1);
        }
        else{
            if(external.charAt(j) == '*') return match(uri, external, i+1, j) || match(uri, external, i+1, j+1);
            return false;
        }
    }

    private boolean match(String uri, String external){
        return match(uri.trim(), external.trim(), 0, 0);
    }

    private boolean isExternal(UserAuthView authView, List<String> uris) {
        for(String uri : uris){
            if(match(authView.getUri(), uri)) return true;
        }
        return false;
    }

    public void readFile(File file, List<UserAuthView> userAuthViews) {
        int dataCount = 0;
        try {
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] split = line.split(",");
                userAuthViews.add(new UserAuthView(new User(null, split[0], split[1], split[2]), split[3], split[4], ""));
                dataCount++;
            }
        } catch (Exception e){
            log.info("Error while reading file.", e);
        }
        log.info("Data read {}", dataCount);
        log.info("Set size {}", userAuthViews.size());
    }
}