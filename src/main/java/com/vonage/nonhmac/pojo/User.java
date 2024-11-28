package com.vonage.nonhmac.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@AllArgsConstructor
@Entity
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String accountId;

    private String userId;

    private String username;

    public static User unknown(){
        return new User(null, "UNKNOWN", "UNKNOWN", "UNKNOWN");
    }

    public static User anonymous(){
        return new User(null, "1", "5", "Anonymous");
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof User)) {
            return false;
        }
        User user = (User) o;
        return user.accountId.equals(this.accountId) &&
                user.userId.equals(this.userId) &&
                user.username.equals(this.username);
    }

    @Override
    public int hashCode() {
        return accountId.hashCode() + userId.hashCode() + username.hashCode();
    }

    @Override
    public String toString() {
        return accountId + ", " + userId + ", " + username;
    }
}
