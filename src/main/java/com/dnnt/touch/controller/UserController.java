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
import com.dnnt.touch.netty.MsgHandler;
import com.dnnt.touch.protobuf.ChatProto;
import com.dnnt.touch.util.Constant;
import com.dnnt.touch.util.MsgApi;
import com.dnnt.touch.util.SecureUtilKt;
import com.dnnt.touch.util.SessionUtil;
import io.netty.channel.ChannelHandlerContext;
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
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/user")
public class UserController extends BaseController{

//    private Algorithm hmacSHA = Algorithm.HMAC256(Constant.TOKEN_KEY);

    private UserMapper userMapper;
    private MsgMapper msgMapper;

    @Autowired
    public UserController(UserMapper userMapper, MsgMapper msgMapper){
        this.userMapper = userMapper;
        this.msgMapper = msgMapper;
    }

    public UserController(){}

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
            String token = null;
            try {
                token = JWT.create()
                        .withClaim(User.ID,user.getId())
                        .sign(Algorithm.HMAC256(Constant.TOKEN_KEY+user.getPassword()));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            user.setPassword("");
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
        if (userName.isEmpty() || userName.length() > 16){
            return generateFailure("用户名，不能为空，最多为16位");
        }
        if (userName.matches("\\d.*")){
            return generateFailure("用户名不得以数字开头!");
        }
        if (password.length() < 6 || password.length() > 16){
            return generateFailure("密码小于6位或大于16位");
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
            System.out.println(phone + " : " + code);
            String msg = MsgApi.sentMsg("【物联网实验室】您的验证码是" + code,phone);
            if (msg.isEmpty()){
                SessionUtil.setAttr(phone,code);
                data = generateSuccessful(null);
            }else {
                data = generateFailure(msg);
            }

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

        User user = SecureUtilKt.verifyToken(token,userMapper);
        if (user == null){
            return generateFailure("wrong token");
        }
        long id = user.getId();
        Set<Long> friendsId = userMapper.getUserFriends(id);
        List<User> users = new ArrayList<>();
        for (long fid : friendsId){
            User u = userMapper.selectById(fid);
            u.setPassword("");
            u.setPhone("");
            users.add(u);
        }
        return generateSuccessful(users);
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
    public Json<String> updateHead(MultipartFile file,String token, HttpServletRequest request){
        long now = System.currentTimeMillis();
        User user = SecureUtilKt.verifyToken(token,userMapper);
        if (user == null){
            return generateFailure("上传头像失败,wrong token");
        }
        String headUrl = user.getHeadUrl();
        long id = user.getId();
        File oldFile;
        int num = 0;
        if (!headUrl.endsWith("default.png")){
            int i = headUrl.lastIndexOf('_') + 1;
            num = Integer.parseInt(headUrl.substring(i,headUrl.length()-4));
            oldFile = new File(getHeadPath(request,id,num));
            oldFile.delete();
            num++;
        }
        File head = new File(getHeadPath(request,id,num));
        try(OutputStream os = new FileOutputStream(head);
            InputStream is = file.getInputStream()){
            IOUtils.copy(is,os);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String path = Constant.MAPPING_HEAD_DIR + String.valueOf(id) + "_" + String.valueOf(num) + ".png";
        userMapper.updateHeadUrl(id,path);

        notifyFriends(id,path,now,Constant.TYPE_HEAD_UPDATE);

        return generateSuccessful(path);
    }

    private void notifyFriends(long id, String msg, long time, int type){
        Set<Long> friendsId;
        ChannelHandlerContext ctx = MsgHandler.ctxMap.get(id);
        if (ctx != null){
            MsgHandler msgHandler = (MsgHandler)ctx.pipeline().get(Constant.MSG_HANDLER);
            friendsId = msgHandler.friendsId;
        }else {
            friendsId = userMapper.getUserFriends(id);
        }
        for (long fid : friendsId){
            ChannelHandlerContext toCtx = MsgHandler.ctxMap.get(fid);
            if (toCtx != null){
                toCtx.executor().execute(() -> {
                    MsgHandler msgHandler = (MsgHandler)toCtx.pipeline().get(Constant.MSG_HANDLER);
                    msgHandler.sendMsg(toCtx,ChatProto.ChatMsg.newBuilder()
                            .setFrom(id)
                            .setTo(fid)
                            .setMsg(msg)
                            .setTime(time)
                            .setType(type)
                            .build());
                });

            }else {
                msgMapper.insertMsg(new IMMsg(id,fid,msg,time,type));
            }
        }
    }

    private String getHeadPath(HttpServletRequest request, long id,int num){
        String dirPath = request.getServletContext().getRealPath(Constant.REAL_HEAD_DIR);
        String imageName = String.valueOf(id) + "_" + String.valueOf(num) + ".png";
        return dirPath + "/" + imageName;
    }

    @RequestMapping("/updateUserName")
    public Json<Void> updateUserName(String newName, String token){
        if (newName.isEmpty() || newName.length() > 16){
            return generateFailure("用户名，不能为空，最多为16位");
        }
        User user = SecureUtilKt.verifyToken(token,userMapper);
        if (user == null){
            return generateFailure("修改用户名失败,wrong token");
        }
        if (userMapper.selectByName(newName) != null){
            return generateFailure("该用户名已被使用！");
        }
        userMapper.updateUserName(user.getId(),newName);

        notifyFriends(user.getId(),newName,System.currentTimeMillis(),Constant.TYPE_UPDATE_USER_NAME);

        return generateSuccessful(null);

    }

    @RequestMapping("/changePassword")
    public Json<Void> changePassword(Long id,String oldPassword, String newPassword){
        if (oldPassword.length() < 6 || oldPassword.length() > 16){
            return generateFailure("密码错误");
        }
        if (newPassword.length() < 6 || newPassword.length() > 16){
            return generateFailure("密码小于6位或大于16位");
        }
        String oldMD5Pwd = SecureUtilKt.getMD5Hex(oldPassword.getBytes());
        User user = userMapper.selectById(id);
        if (oldMD5Pwd.equals(user.getPassword())){
            String newMD5Pwd = SecureUtilKt.getMD5Hex(newPassword.getBytes());
            userMapper.updatePassword(user.getPhone(),newMD5Pwd);
            return generateSuccessful(null);
        }else {
            return generateFailure("密码错误");
        }
    }

    @RequestMapping("uploadErrFile")
    public Json<Void> uploadErrFile(MultipartFile uploadFile, HttpServletRequest request){
        String dirPath = request.getServletContext().getRealPath("/app_err/");
        new File(dirPath).mkdir();
        File file = new File(dirPath + "/"  + uploadFile.getOriginalFilename());
        try(OutputStream os = new FileOutputStream(file);
            InputStream is = uploadFile.getInputStream()){
            IOUtils.copy(is,os);
            return generateSuccessful(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return generateFailure("");
    }





    @RequestMapping("/test")
    public Json<Long> test(String token){
        long id = JWT.decode(token).getClaim(User.ID).asLong();
        return generateSuccessful(id);
    }
}
