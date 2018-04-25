package com.dnnt.touch.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.dnnt.touch.domain.IMMsg;
import com.dnnt.touch.domain.Json;
import com.dnnt.touch.mapper.MsgMapper;
import com.dnnt.touch.mapper.UserMapper;
import com.dnnt.touch.util.Constant;
import com.dnnt.touch.util.SecureUtilKt;
import com.dnnt.touch.util.SessionUtil;
import org.apache.commons.io.IOUtils;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.dnnt.touch.domain.User;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/user")
public class UserController extends BaseController{

//    private Algorithm hmacSHA = Algorithm.HMAC256(Constant.TOKEN_KEY);

    private UserMapper userMapper;
    private MsgMapper msgMapper;

    @Autowired
    public UserController(UserMapper userMapper, MsgMapper msgMapper) throws UnsupportedEncodingException {
        this.userMapper = userMapper;
        this.msgMapper = msgMapper;
    }

    public UserController() throws UnsupportedEncodingException {}

    @RequestMapping("/login")
    public Json<User> login(String nameOrPhone , String password) {
        if (nameOrPhone.length() < 4 || nameOrPhone.length() > 16){
            return generateFailure("用户名/手机错误!");
        }
        //查找用户是否存在
        User user;
        if (nameOrPhone.matches("\\d.*")){
            user = userMapper.selectByPhone(nameOrPhone);
        }else {
            user = userMapper.selectByName(nameOrPhone);
        }

        if (user == null){
            return generateFailure("用户名/手机错误");
        }

        Json<User> data;
        String md5Pwd = SecureUtilKt.getMD5Hex(password.getBytes());
        if (md5Pwd.equals(user.getPassword())){
            user.setPassword("");
            //生成并设置token,一星期后过期
            String token = null;
            try {
                token = JWT.create()
    //                    .withExpiresAt(new Date(System.currentTimeMillis() + 7*24*60*60*1000))
                        .withClaim(User.USER_NAME,user.getUserName())
                        .withClaim(User.PHONE,user.getPhone())
                        .withClaim(User.ID,user.getId())
                        .sign(Algorithm.HMAC256(Constant.TOKEN_KEY));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            data = generateSuccessful(user);
            data.setMsg(token);
        }else {
            data = generateFailure("用户名或密码错误!");
        }
        return data;
    }

    @RequestMapping("/register")
    public Json<Void> register(String userName, String password, String phone, String verificationCode){
        //verificationCode
        Object code = SessionUtil.getAttr(phone);
        if (code == null || !code.equals(verificationCode)){
            return generateFailure("非法注册!!!");
        }
        if (userName.length() < 4 || userName.length() > 16){
            return generateFailure("用户名至少为4位，最多为16位");
        }
        if (userName.matches("\\d.*")){
            return generateFailure("用户名不得以数字开头!");
        }
        if (password.length() < 6){
            return generateFailure("密码至少为6位");
        }

        Json<Void> data;
        User user = userMapper.selectByName(userName);
        if (user == null){
            String md5Pwd = SecureUtilKt.getMD5Hex(password.getBytes());
            user = new User();
            user.setUserName(userName);
            user.setPassword(md5Pwd);
            user.setPhone(phone);
            userMapper.insertUser(user);
            data = generateSuccessful(null);
        }else {
            data = generateFailure("用户名已存在!");
        }
        return data;
    }

    @RequestMapping("/getVerificationCode")
    public Json<Void> getVerificationCode(String phone,int codeTag){
        if (phone.length() != 11){
            return generateFailure("手机格式不正确");
        }
        Json<Void> data = null;
        User user = userMapper.selectByPhone(phone);
        if ((codeTag == Constant.CODE_TAG_REGISTER && user == null) ||
                (codeTag == Constant.CODE_TAG_RESET && user != null)){
            Random random = new Random(System.currentTimeMillis());
            String code = String.valueOf(random.nextInt(1000000));
            while (code.length() < 6){
                code = "0" + code;
            }
            //TODO 发送手机验证码
            SessionUtil.setAttr(phone,code);
            System.out.println(phone + " : " + code);
            data = generateSuccessful(null);
        }else if (codeTag == Constant.CODE_TAG_REGISTER){
            data = generateFailure("该手机号已注册");
        }else if (codeTag == Constant.CODE_TAG_RESET){
            data = generateFailure("该手机号尚未注册");
        }
        return data;
    }

    @RequestMapping("/codeVerification")
    public Json<Void> codeVerification(String phone, String verificationCode){
        Object code = SessionUtil.getAttr(phone);
        if (code == null){
            return generateFailure("验证码过期或未获取验证码");
        }
        System.out.println(code);
        Json<Void> data;
        if (code.equals(verificationCode)){
            data = generateSuccessful(null);
        }else {
            data = generateFailure("验证码错误");
        }
        return data;
    }

    @RequestMapping("/getFriends")
    public Json<List<User>> getFriends(String token){
//        JWT.require(hmacSHA)
        try {
            DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(Constant.TOKEN_KEY))
                    .build()
                    .verify(token);
            long id = decodedJWT.getClaim(User.ID).asLong();
            List<Long> friendsId = userMapper.getUserFriends(id);
            List<User> users = new ArrayList<>();
            for (long fid : friendsId){
                User u = userMapper.selectById(fid);
                u.setPassword("");
                u.setPhone("");
                users.add(u);
            }
            return generateSuccessful(users);
        }catch (Exception e){
            return generateFailure("unknown error");
        }
    }

    @RequestMapping("/resetPassword")
    public Json<Void> resetPassword(String password,String phone,String verificationCode){
        Object code = SessionUtil.getAttr(phone);
        if (code == null || !code.equals(verificationCode)){
            return generateFailure("非法操作");
        }
        String md5Pwd = SecureUtilKt.getMD5Hex(password.getBytes());
        userMapper.updatePassword(phone,md5Pwd);
        return generateSuccessful(null);
    }

    @RequestMapping("updateHead")
    public Json<String> updateHead(@RequestParam("file") MultipartFile file,@RequestParam("token") String token, HttpServletRequest request){
        DecodedJWT decodedJWT = null;
        long now = System.currentTimeMillis();
        try {
            decodedJWT = JWT.require(Algorithm.HMAC256(Constant.TOKEN_KEY))
                    .build()
                    .verify(token);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return generateFailure("wrong token");
        }
        long id = decodedJWT.getClaim(User.ID).asLong();
        User user = userMapper.selectById(id);
        String headUrl = user.getHeadUrl();
        File oldFile = null;
        int num = 0;
        if (!headUrl.endsWith("default.png")){
            int i = headUrl.lastIndexOf('_') + 1;
            num = Integer.parseInt(headUrl.substring(i,headUrl.length()-4));
            oldFile = new File(getHeadPath(request,id,num));
            oldFile.delete();
            num++;
        }
        File head = new File(getHeadPath(request,id,num));
        OutputStream os = null;
        try {
            head.createNewFile();
            os = new FileOutputStream(head);
            IOUtils.copy(file.getInputStream(),os);
        } catch (Exception e) {
            e.printStackTrace();
            return generateFailure("");
        }
        String path = Constant.MAPPING_HEAD_DIR + String.valueOf(id) + "_" + String.valueOf(num) + ".png";
        userMapper.updateHeadUrl(id,path);
        List<Long> friedsId = userMapper.getUserFriends(id);
        for (long fid : friedsId){
            msgMapper.insertMsg(new IMMsg(id,fid,path,now,Constant.TYPE_HEAD_UPDATE));
        }
        return generateSuccessful(path);
    }

    private String getHeadPath(HttpServletRequest request, long id,int num){
        String dirPath = request.getServletContext().getRealPath(Constant.REAL_HEAD_DIR);
        String imageName = String.valueOf(id) + "_" + String.valueOf(num) + ".png";
        return dirPath + "/" + imageName;
    }

    @RequestMapping("/test")
    public Json<Void> test(){
        return generateSuccessful(null);
    }
}
