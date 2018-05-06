package com.dnnt.touch;


import com.dnnt.touch.domain.User;
import com.dnnt.touch.mapper.UserMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.Set;


@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration("src/main/resources")
@ContextConfiguration(locations = {"classpath:springMVC.xml"})
public class MybatisTest {
    @Autowired
    UserMapper userMapper;
    @Test
    public void userTest(){
        User user = userMapper.selectById(3);
        if (user != null){
            System.out.println(user.getPhone());
        }
    }

    @Test
    public void getFriendsTest(){
        Set<Long> friends = userMapper.findUserFriends(3);
        friends.forEach(System.out::println);
    }

}
