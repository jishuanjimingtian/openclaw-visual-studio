-- OpenClaw Visual Studio 初始数据库表结构

-- 会话表
CREATE TABLE conversations (
    id          VARCHAR(36)  PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    model       VARCHAR(100) NOT NULL DEFAULT 'gpt-4',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tags        VARCHAR(500),
    archived    BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 消息表
CREATE TABLE messages (
    id              VARCHAR(36)  PRIMARY KEY,
    conversation_id VARCHAR(36)  NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20)  NOT NULL,
    content         CLOB         NOT NULL,
    tokens          INTEGER,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_conversation ON messages(conversation_id);

-- 模型配置表
CREATE TABLE model_configs (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    provider    VARCHAR(50)  NOT NULL,
    endpoint    VARCHAR(500) NOT NULL,
    api_key     VARCHAR(500),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 路由策略表
CREATE TABLE routing_policies (
    id           VARCHAR(36)  PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    strategy     VARCHAR(50)  NOT NULL,
    model_config_id VARCHAR(36) REFERENCES model_configs(id),
    priority     INTEGER      NOT NULL DEFAULT 0,
    conditions   CLOB,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 部署任务表
CREATE TABLE deployment_tasks (
    id          VARCHAR(36)  PRIMARY KEY,
    status      VARCHAR(20)  NOT NULL DEFAULT 'pending',
    progress    INTEGER      NOT NULL DEFAULT 0,
    start_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time    TIMESTAMP,
    log         CLOB,
    config_json CLOB
);

-- Skill 表
CREATE TABLE installed_skills (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    version     VARCHAR(50)  NOT NULL,
    author      VARCHAR(200),
    description CLOB,
    source      VARCHAR(50)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'installed',
    installed_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rating      DOUBLE       DEFAULT 0.0,
    downloads   INTEGER      DEFAULT 0,
    license     VARCHAR(100)
);

-- 系统配置键值表
CREATE TABLE system_configs (
    config_key   VARCHAR(200) PRIMARY KEY,
    config_value CLOB,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
