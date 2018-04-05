package com.dnnt.touch.controller;

import com.dnnt.touch.domain.Json;

public class BaseController {
    public <T> Json<T> generateSuccessful(T obj){
        return new Json<>("successful",true,obj,200);
    }

    public <T> Json<T> generateFailure(String msg){
        return new Json<>(msg,false,null,-1);
    }
}
