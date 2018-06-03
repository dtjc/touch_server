package com.dnnt.touch.util;

import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MsgApi {
    private static final String URl_SENT_MSG = "http://yunpian.com/v1/sms/send.json";
    private static final String ENCODING = "UTF-8";

    public static String sentMsg(String text, String mobile) {
        Map<String ,String> params = new HashMap<>();
        params.put("apikey",apikey);
        params.put("text",text);
        params.put("mobile",mobile);
        String responseMsg = post(URl_SENT_MSG,params);
        int statusCode = Integer.parseInt(JSONObject.fromObject(responseMsg).get("code").toString());
        switch (statusCode){
            case 0:
                return "";
            case 8:
                return "同一手机号30秒内重复提交相同的内容!";
            case 9:
                return "同一手机号5分钟内重复提交相同的内容超过3次!";
            case 10:
                return "该号码在短信验证黑名单中，请联系客服!";
            case 17:
                return "24小时内该手机号获取验证码次数超过限制!";
            case 22:
                return "1小时内同一手机号发送次数超过限制!";
            case -51:
                return "系统繁忙，请稍后再试!";
            default:
                return "发送失败，未知错误!";
        }
    }

    private static String post(String url, Map<String, String> paramsMap) {
        CloseableHttpClient client = HttpClients.createDefault();
        String responseMsg = "";
        CloseableHttpResponse response = null;
        try {
            HttpPost method = new HttpPost(url);
            if (paramsMap!=null) {
                List<NameValuePair> paramList = new ArrayList<>();
                for(Map.Entry<String ,String> param:paramsMap.entrySet()){
                    paramList.add(new BasicNameValuePair(param.getKey(),param.getValue()));
                }
                method.setEntity(new UrlEncodedFormEntity(paramList,ENCODING));
            }

            response = client.execute(method);

            if(response.getStatusLine().getStatusCode()==200){
                HttpEntity httpEntity = response.getEntity();
                if(httpEntity!=null) {
                    responseMsg = EntityUtils.toString(httpEntity);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            try {
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return responseMsg;
    }
}
