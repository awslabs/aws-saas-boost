package com.amazon.aws.partners.saasfactory.saasboost;

import java.util.List;
import java.util.Map;

public interface SystemUserDataAccessLayer {

    List<SystemUser> getUsers(Map<String, Object> event);

    SystemUser getUser(Map<String, Object> event, String username);

    SystemUser updateUser(Map<String, Object> event, SystemUser user);

    SystemUser enableUser(Map<String, Object> event, String username);

    SystemUser disableUser(Map<String, Object> event, String username);

    SystemUser insertUser(Map<String, Object> event, SystemUser user);

    void deleteUser(Map<String, Object> event, String username);
}
