package com.vonage.nonhmac.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import javax.persistence.*;

@Getter
@Service
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class NonHmacData {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL)
    private User user;

    @OneToOne(cascade = CascadeType.ALL)
    private RequestIDData requestIDData;

    @Override
    public String toString() {
        return user.getAccountId() +
                ", " +
                user.getUserId() +
                ", " +
                user.getUsername() +
                ", " +
                requestIDData.getAuthType() +
                ", " +
                requestIDData.getUri() +
                ", " +
                requestIDData.getRequestId();
    }
}
