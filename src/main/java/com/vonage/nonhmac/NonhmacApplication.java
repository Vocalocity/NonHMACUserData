package com.vonage.nonhmac;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ComponentScan("com.vonage")
@EnableAsync
public class NonhmacApplication {

	private static boolean match(String uri, String external, int i, int j){
		if(i == uri.length()){
			return j == external.length();
		}
		if(j == external.length()){
			return false;
		}
		if(external.charAt(j) == uri.charAt(i)){
			return match(uri, external, i+1, j+1);
		}
		else{
			if(external.charAt(j) == '*') return match(uri, external, i+1, j) || match(uri, external, i+1, j+1);
			return false;
		}
	}

	public static void main(String[] args) {
		System.out.println(match("/appserver/rest/click2callmeToken/IWeCEPzhDpDGrPIN7LSTiTx9H%2Bjw2GY%2FOP9S3LvBtzNpG624fCTDl2YiLyM1YVcHUmPuhfZ3CButs4ShJrAKNA%3D%3D", "/appserver/rest/click2callmeToken/*", 0, 0));
		System.out.println(match("abchgduhsd", "*", 0, 0));
		System.out.println(match("abcsd", "ab*", 0, 0));
		System.out.println(match("abcsd", "ab*s", 0, 0));


		SpringApplication.run(NonhmacApplication.class, args);
	}
}