package com.vonage.nonhmac.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Getter
@Setter
public class RequestIDData {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String requestId;

    private String authType;

    private String uri;
}
