package com.windcoder.qycms.core.system.shiro;


import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.cache.CacheManager;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.session.mgt.SessionManager;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.spring.web.config.DefaultShiroFilterChainDefinition;
import org.apache.shiro.spring.web.config.ShiroFilterChainDefinition;
import org.apache.shiro.web.filter.authc.LogoutFilter;


import org.crazycake.shiro.RedisCacheManager;
import org.crazycake.shiro.RedisManager;
import org.crazycake.shiro.RedisSessionDAO;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;


import javax.servlet.Filter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * https://blog.csdn.net/u013615903/article/details/78781166
 */
@Configuration
public class ShiroConfiguration {

    @Autowired
    RedisProperties redisProperties;


    /**
     *  开启shiro aop注解支持.
     *  使用代理方式;所以需要开启代码支持;
     * @param securityManager
     * @return
     */
    @Bean("authorizationAttributeSourceAdvisor")
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(SecurityManager securityManager) {
        //AuthorizationAttributeSourceAdvisor aasa = new AuthorizationAttributeSourceAdvisor();
        AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor  = new AuthorizationAttributeSourceAdvisor();
        authorizationAttributeSourceAdvisor .setSecurityManager(securityManager);
        return authorizationAttributeSourceAdvisor ;
    }


    @Bean
    public ShiroFilterFactoryBean shirFilter(SecurityManager securityManager) {
        ShiroFilterFactoryBean shiroFilterFactoryBean = new ShiroFilterFactoryBean();
        shiroFilterFactoryBean.setSecurityManager(securityManager);


        Map<String, Filter> filters = new LinkedHashMap<String, Filter>();
        LogoutFilter logoutFilter = new LogoutFilter();
        logoutFilter.setRedirectUrl("/login");
        filters.put("logout", logoutFilter);
//        FormAuthenticationFilter formAuthenticationFilter = new SimpleFormAuthenticationFilter();
//        filters.put("authc", formAuthenticationFilter);
//        ApiAuthenticationFilter apiAuthenticationFilter = new ApiAuthenticationFilter();
//        apiAuthenticationFilter.setLoginUrl("/api/login");
//        filters.put("autha", apiAuthenticationFilter);
        shiroFilterFactoryBean.setFilters(filters);


        Map<String, String> filterChainDefinitionMap = new LinkedHashMap<String, String>();
        //注意过滤器配置顺序 不能颠倒
        //配置退出 过滤器,其中的具体的退出代码Shiro已经替我们实现了，登出后跳转配置的loginUrl
        filterChainDefinitionMap.put("/logout", "logout");
        // 配置不会被拦截的链接 顺序判断
        filterChainDefinitionMap.put("/static/**", "anon");
        filterChainDefinitionMap.put("/api/login", "anon");
        filterChainDefinitionMap.put("/websocket", "anon");
        filterChainDefinitionMap.put("/websocket2", "anon");
        filterChainDefinitionMap.put("/api/users/**", "anon");
        filterChainDefinitionMap.put("/login", "anon");
        filterChainDefinitionMap.put("/**", "authc");
        //配置shiro默认登录界面地址，前后端分离中登录界面跳转应由前端路由控制，后台仅返回json数据
        shiroFilterFactoryBean.setLoginUrl("/api/unauth");
        shiroFilterFactoryBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
        return shiroFilterFactoryBean;
    }

    /**
     * 凭证匹配器
     * @return
     */
    @Bean
    public HashedCredentialsMatcher hashedCredentialsMatcher(CacheManager cacheManager) {
        RetryLimitHashedCredentialsMatcher hashedCredentialsMatcher = new RetryLimitHashedCredentialsMatcher(cacheManager);
        hashedCredentialsMatcher.setHashAlgorithmName(PasswordHelper.ALGORITHM);//散列算法:这里使用SHA-1算法;
        hashedCredentialsMatcher.setHashIterations(PasswordHelper.HASHITERATIONS);//散列的次数，比如散列两次，相当于 SHA-1(SHA-1(""));
        return hashedCredentialsMatcher;
    }

    /**
     *  自定义Realm，用于设置登录以及授权逻辑。
     *  spring允许用户通过depends-on属性指定bean前置依赖的bean,前置依赖的bean会在本bean实例化之前创建好
     *
     *  Realm：域，Shiro从从Realm获取安全数据（如用户、角色、权限）：
     *  就是说SecurityManager要验证用户身份，那么它需要从Realm获取相应的用户进行比较以确定用户身份是否合法；
     *  也需要从Realm得到用户相应的角色/权限进行验证用户是否能进行操作；
     *  可以把Realm看成DataSource，即安全数据源。
     *  如我们之前的ini配置方式将使用org.apache.shiro.realm.text.IniRealm。
     * @param hashedCredentialsMatcher
     * @return
     */
    @Bean
    @DependsOn("lifecycleBeanPostProcessor")
    public Realm myShiroRealm(HashedCredentialsMatcher hashedCredentialsMatcher) {
        UserRealm myShiroRealm = new UserRealm();
        myShiroRealm.setCredentialsMatcher(hashedCredentialsMatcher);
        return myShiroRealm;
    }

    /**
     * 配置shiro redisManager
     * redisProperties会自动读取application.properties中关于redis的配置
     * @return
     */
    @Bean
    public RedisManager redisManager() {
        RedisManager redisManager = new RedisManager();
        redisManager.setHost(redisProperties.getHost());
        redisManager.setPort(redisProperties.getPort());
        redisManager.setTimeout(redisProperties.getTimeout());
        redisManager.setPassword(redisProperties.getPassword());
        return redisManager;
    }

//    @Bean
//    public SecurityManager securityManager(Realm myShiroRealm,SessionManager sessionManager,CacheManager cacheManager,Authenticator authenticator) {
//        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
//        securityManager.setRealm(myShiroRealm);
//        // 自定义session管理 使用redis
//        securityManager.setSessionManager(sessionManager);
//        // 自定义缓存实现 使用redis
//        securityManager.setCacheManager(cacheManager);
//        securityManager.setAuthenticator(authenticator);
//        return securityManager;
//    }


    /**
     *  自定义sessionManager
     * @param redisSessionDAO
     * @return
     */
    @Bean
    public SessionManager sessionManager(RedisSessionDAO redisSessionDAO) {
        MySessionManager mySessionManager = new MySessionManager();
        mySessionManager.setSessionDAO(redisSessionDAO);
        return mySessionManager;
    }

    /**
     * RedisSessionDAO shiro sessionDao层的实现 通过redis
     * <p>
     * 使用的是shiro-redis开源插件
     */
    @Bean
    public RedisSessionDAO redisSessionDAO(RedisManager redisManager) {
        RedisSessionDAO redisSessionDAO = new RedisSessionDAO();
        redisSessionDAO.setRedisManager(redisManager);
        return redisSessionDAO;
    }


    /**
     * cacheManager 缓存 redis实现
     * @param redisManager
     * @return
     */
    @Bean("shiroRedisCacheManager")
    public CacheManager cacheManager(RedisManager redisManager) {
        RedisCacheManager redisCacheManager = new RedisCacheManager();
        redisCacheManager.setRedisManager(redisManager);
        return redisCacheManager;
    }


    /**
     * 这里使用shiro默认拦截器ShiroFilterChainDefinition
     *
     * @return
     */
    @Bean
    public ShiroFilterChainDefinition shiroFilterChainDefinition() {
        DefaultShiroFilterChainDefinition chainDefinition = new DefaultShiroFilterChainDefinition();
        return chainDefinition;
    }

    /**
     * shiro自动代理
     * DelegatingFilterProxy作用是自动到spring容器查找名字为shiroFilter（filter-name）的bean并把所有Filter的操作委托给它。
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator daap = new DefaultAdvisorAutoProxyCreator();
        // shiro starter 默认实现未设置此属性，会导致开启事务的Service无法注入，因此替换默认设置
        daap.setProxyTargetClass(true);
        return daap;
    }

    /**
     * 注册全局异常处理
     * @return
     */
//    @Bean(name = "exceptionHandler")
//    public HandlerExceptionResolver handlerExceptionResolver() {
//        return new MyExceptionHandler();
//    }




}
