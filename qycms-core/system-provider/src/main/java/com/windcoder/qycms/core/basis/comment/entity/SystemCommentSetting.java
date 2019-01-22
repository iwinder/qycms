package com.windcoder.qycms.core.basis.comment.entity;


import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="sns_system_comment_agent")
public class SystemCommentSetting {
    @Id
    @GeneratedValue
    private Long id;
    /**
     * 是否可用
     */
    private Boolean isEnabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Boolean enabled) {
        this.isEnabled = enabled;
    }
}
