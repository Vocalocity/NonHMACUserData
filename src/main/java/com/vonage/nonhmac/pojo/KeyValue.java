package com.vonage.nonhmac.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(indexes = @Index(columnList = "key"))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class KeyValue {

    @Id
    private Long id;

    private String key;

    private String value;
}
