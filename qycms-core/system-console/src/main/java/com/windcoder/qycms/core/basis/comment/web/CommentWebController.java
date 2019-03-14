package com.windcoder.qycms.core.basis.comment.web;

import com.fasterxml.jackson.annotation.JsonView;
import com.windcoder.qycms.core.basis.comment.dto.CommentDto;
import com.windcoder.qycms.core.basis.comment.dto.CommentParentDto;
import com.windcoder.qycms.core.basis.comment.entity.Comment;
import com.windcoder.qycms.core.basis.comment.entity.CommentAgent;
import com.windcoder.qycms.core.basis.comment.entity.SystemCommentSetting;
import com.windcoder.qycms.core.basis.comment.service.CommentAgentService;
import com.windcoder.qycms.core.basis.comment.service.CommentService;
import com.windcoder.qycms.core.basis.comment.service.SystemCommentSettingService;
import com.windcoder.qycms.exception.BusinessException;
import com.windcoder.qycms.utils.CookieUtils;
import com.windcoder.qycms.utils.ModelMapperUtils;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("api/web/comments/{agentTargetId}")
public class CommentWebController {

    private static final Logger logger = LoggerFactory.getLogger(CommentWebController.class);

    @Autowired
    CommentService commentService;
    @Autowired
    CommentAgentService agentTargetService;
    @Autowired
    SystemCommentSettingService systemCommentSettingService;

    @GetMapping(value = "")
    public Page<CommentDto> list(@PathVariable("agentTargetId") Long agentTargetId,
                              @PageableDefault(sort = "id", direction = Direction.DESC) Pageable pageable) {
        checkAndGetCommentAgent(agentTargetId);
        Page<Comment> comments = commentService.findTopLevelComments(agentTargetId, pageable);
        Type type = new TypeToken<List<CommentDto>>() {}.getType();
        List<CommentDto> commentsDto = ModelMapperUtils.map(comments.getContent(),type);
        return  new PageImpl<>(commentsDto,pageable,comments.getTotalElements());
    }

    @PostMapping(value = "/add")
    public CommentDto add(@PathVariable("agentTargetId") Long agentTargetId, Comment comment, HttpServletResponse response) {
        try {
            CommentAgent agentTarget = checkAndGetCommentAgent(agentTargetId);
            comment.setTarget(agentTarget);
            comment.setDepth(1);
            checkAndEditStatusIsENROLLED(comment);
            Date now = new Date();
            comment.setCreatedDate(now);
            comment.setLastModifiedDate(now);
            comment = commentService.save(comment);
            setCommentAuthorIntoCookie(response,comment.getAuthor(),comment.getEmail(),comment.getUrl());
            return ModelMapperUtils.map(comment, CommentDto.class);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new BusinessException("添加评论失败");
        }
    }

    @GetMapping(value = "/{commentId}/replies")
    public Page<CommentParentDto> replies(@PathVariable("agentTargetId") Long agentTargetId,
                                 @PathVariable("commentId") Long parentId,
                                 @PageableDefault(sort = "createdDate", direction = Direction.ASC) Pageable pageable) {
        checkAndGetCommentAgent(agentTargetId);
        Page<Comment> comments = commentService.findComments(agentTargetId, parentId, pageable);
        Type type = new TypeToken<List<CommentParentDto>>(){}.getType();
        List<CommentParentDto> commentsDto = ModelMapperUtils.map(comments.getContent(),type);
        return  new PageImpl<>(commentsDto,pageable,comments.getTotalElements());
    }

    @PostMapping(value = "/{commentId}/replies/add")
    public CommentDto addReply(@PathVariable("agentTargetId") Long agentTargetId,
                            @PathVariable("commentId") Long parentId,Comment comment, HttpServletResponse response) {
        try {
            CommentAgent agentTarget = checkAndGetCommentAgent(agentTargetId);
            Comment parent = commentService.findOne(parentId);
            Comment topParent = null == parent.getTopParent() ? parent : parent.getTopParent();
            Integer level = parent.getDepth() + 1;

            comment.setTarget(agentTarget);
            comment.setParent(parent);
            comment.setTopParent(topParent);
            comment.setDepth(level);

            checkAndEditStatusIsENROLLED(comment);

            Date now = new Date();
            comment.setCreatedDate(now);
            comment.setLastModifiedDate(now);
            comment = commentService.save(comment);
            setCommentAuthorIntoCookie(response,comment.getAuthor(),comment.getEmail(),comment.getUrl());
            return ModelMapperUtils.map(comment, CommentDto.class);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new BusinessException("添加评论失败");
        }
    }

    private CommentAgent checkAndGetCommentAgent( Long agentTargetId){
        SystemCommentSetting sysCommentSetting= systemCommentSettingService.findSysForumSetting();
        if(null == sysCommentSetting.getIsEnabled() || !sysCommentSetting.getIsEnabled() ) {
            throw new BusinessException("未开启评论功能，不能进行评论");
        }
        CommentAgent agentTarget = agentTargetService.findOne(agentTargetId);
        if(null == agentTarget.getIsEnabled() || !agentTarget.getIsEnabled()){
            throw new BusinessException("未开启评论功能，不能进行评论");
        }
        return agentTarget;
    }

    private void checkAndEditStatusIsENROLLED(Comment comment){
        Integer countByEmail =commentService.countByEmail(comment.getEmail());
        if(countByEmail>0){
            Integer count =commentService.countByEmailAndStatusNotEnrolled(comment.getEmail());
            if(count==0){
                comment.setStatus("ENROLLED");
            }
        }
    }

    private void setCommentAuthorIntoCookie(HttpServletResponse response, String author, String email, String url) throws UnsupportedEncodingException {


        CookieUtils.setCookie(response,"comment_remember_author", URLEncoder.encode(author, "UTF-8"), CookieUtils.DEFAULTMAXAGE);
        CookieUtils.setCookie(response,"comment_remember_mail", URLDecoder.decode(email, "UTF-8"),  CookieUtils.DEFAULTMAXAGE);
        if (StringUtils.isNotBlank(url)) {
            CookieUtils.setCookie(response,"comment_remember_url", URLEncoder.encode(url, "UTF-8"),  CookieUtils.DEFAULTMAXAGE);
        }


    }

}
