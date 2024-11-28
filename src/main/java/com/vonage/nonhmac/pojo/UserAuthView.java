package com.vonage.nonhmac.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserAuthView {

    public User user;

    public String authToken;

    public String uri;

    public String accountName;

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof UserAuthView)) {
            return false;
        }
        UserAuthView other = (UserAuthView) o;
        return user.equals(other.getUser()) && authToken.equals(other.getAuthToken()) && accountName.equals(other.getAccountName());
    }

    @Override
    public int hashCode() {
        return user.hashCode() + authToken.hashCode() + accountName.hashCode();
    }

    @Override
    public String toString() {
        return user.toString() + ", " + authToken + ", " + accountName;
    }
}
