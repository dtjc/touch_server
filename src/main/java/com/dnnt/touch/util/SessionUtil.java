package com.dnnt.touch.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpSession;

public class SessionUtil {
    public static HttpSession getSession(){
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attr.getRequest().getSession();
        } catch (IllegalStateException e) {
            throw new RuntimeException("obtain session fail", e);
        }
    }

    public static void setAttr(String key,Object value){
        getSession().setAttribute(key,value);
    }

    public static Object getAttr(String key){
        return getSession().getAttribute(key);
    }
}
