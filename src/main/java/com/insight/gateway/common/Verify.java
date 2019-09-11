package com.insight.gateway.common;

import com.insight.util.Json;
import com.insight.util.Redis;
import com.insight.util.ReplyHelper;
import com.insight.util.Util;
import com.insight.util.pojo.AccessToken;
import com.insight.util.pojo.Reply;
import com.insight.util.pojo.TokenInfo;
import com.insight.util.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author 宣炳刚
 * @date 2019/05/20
 * @remark 用户身份验证类
 */
public class Verify {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 令牌哈希值
     */
    private final String hash;

    /**
     * 缓存中的令牌信息
     */
    private TokenInfo basis;

    /**
     * 令牌ID
     */
    private String tokenId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 构造方法
     *
     * @param token       访问令牌
     * @param fingerprint 用户特征串
     */
    public Verify(String token, String fingerprint) {
        // 初始化参数
        hash = Util.md5(token + fingerprint);
        AccessToken accessToken = Json.toAccessToken(token);
        if (accessToken == null) {
            logger.error("提取验证信息失败。TokenManage is:" + token);

            return;
        }

        tokenId = accessToken.getId();
        userId = accessToken.getUserId();
        basis = getToken();
    }

    /**
     * 验证Token合法性
     *
     * @param authCodes 接口授权码
     * @return Reply Token验证结果
     */
    public Reply compare(String authCodes) {
        if (basis == null) {
            return ReplyHelper.invalidToken();
        }

        if (isInvalid()) {
            return ReplyHelper.fail("用户已被禁用");
        }

        // 验证令牌
        if (!basis.verifyToken(hash)) {
            return ReplyHelper.invalidToken();
        }

        if (basis.isExpiry(true)) {
            return ReplyHelper.expiredToken();
        }

        if (basis.isFailure()) {
            return ReplyHelper.invalidToken();
        }

        // 无需鉴权,返回成功
        if (authCodes == null || authCodes.isEmpty()) {
            return ReplyHelper.success();
        }

        // 进行鉴权,返回鉴权结果
        if (isPermit(authCodes)) {
            return ReplyHelper.success();
        }

        String account = getUser().getAccount();
        logger.warn("用户『" + account + "』试图使用未授权的功能:" + authCodes);

        return ReplyHelper.noAuth();
    }

    /**
     * 获取令牌中的用户ID
     *
     * @return 是否同一用户
     */
    public boolean userIsEquals(String userId) {
        return this.userId.equals(userId);
    }

    /**
     * 获取缓存中的Token
     *
     * @return TokenInfo
     */
    public TokenInfo getBasis() {
        return basis;
    }

    /**
     * 获取令牌持有人的用户ID
     *
     * @return 用户ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 获取令牌持有人的用户名
     *
     * @return 用户名
     */
    public String getUserName() {
        return getUser().getName();
    }

    /**
     * 根据令牌ID获取缓存中的Token
     *
     * @return TokenInfo(可能为null)
     */
    private TokenInfo getToken() {
        String key = "Token:" + tokenId;
        String json = Redis.get(key);

        return Json.toBean(json, TokenInfo.class);
    }

    /**
     * 用户是否被禁用
     *
     * @return User(可能为null)
     */
    private boolean isInvalid() {
        String key = "User:" + userId;
        String value = Redis.get(key, "IsInvalid");
        if (value == null) {
            return true;
        }

        return Boolean.parseBoolean(value);
    }

    /**
     * 读取缓存中的用户数据
     *
     * @return 用户数据
     */
    private User getUser() {
        String key = "User:" + userId;
        String value = Redis.get(key, "User");

        return Json.toBean(value, User.class);
    }

    /**
     * 指定的功能是否授权给用户
     *
     * @param authCodes 接口授权码
     * @return 功能是否授权给用户
     */
    private Boolean isPermit(String authCodes) {
        List<String> functions = basis.getPermitFuncs();
        String[] codes = authCodes.split(",");
        for (String code : codes) {
            if (functions.stream().anyMatch(i -> i.equalsIgnoreCase(code))) {
                return true;
            }
        }

        return false;
    }
}
