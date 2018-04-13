package com.dnnt.touch.mapper;

import com.dnnt.touch.domain.IMMsg;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MsgMapper {
    void insertMsg(@Param("msg") IMMsg msg);
    List<IMMsg> getMsg(@Param("id") long id);
    void deleteMsg(@Param("id") long id);
    IMMsg selectAddFriendMsg(@Param("fromId") long fromId,@Param("toId") long toId,@Param("type") int type);

}
