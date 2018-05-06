package com.dnnt.touch.mapper;

import com.dnnt.touch.domain.User;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface UserMapper {
    //此处的password是经过MD5散列的，不是原始的密码
    void insertUser(@Param("user") User user);
    User selectByName(@Param("name") String userName);
    User selectByPhone(@Param("phone") String phone);
    User selectById(@Param("id") long id);
    void insertFriend(@Param("userId") long userId,@Param("friendId") long friendId);
    Set<Long> getUserFriends(@Param("id") long id);
    void updatePassword(@Param("phone") String phone,@Param("password") String password);
    void updateHeadUrl(@Param("id") long id, @Param("headUrl") String headUrl);
    Set<Long> findUserFriends(@Param("id") long id);
}
