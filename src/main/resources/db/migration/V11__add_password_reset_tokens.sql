IF OBJECT_ID(N'[dbo].[password_reset_tokens]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[password_reset_tokens] (
        [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [user_id] BIGINT NOT NULL,
        [token] NVARCHAR(120) NOT NULL,
        [expires_at] DATETIME2 NOT NULL,
        [used_at] DATETIME2 NULL,
        [created_at] DATETIME2 NOT NULL CONSTRAINT [DF_password_reset_tokens_created_at] DEFAULT GETDATE()
    );
END;

IF OBJECT_ID(N'[dbo].[password_reset_tokens]', N'U') IS NOT NULL
BEGIN
    IF OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
       AND NOT EXISTS (
            SELECT 1
            FROM sys.foreign_keys
            WHERE [name] = N'FK_password_reset_tokens_user'
       )
    BEGIN
        ALTER TABLE [dbo].[password_reset_tokens]
            ADD CONSTRAINT [FK_password_reset_tokens_user]
                FOREIGN KEY ([user_id]) REFERENCES [dbo].[users]([id]);
    END;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE [name] = N'UX_password_reset_tokens_token'
          AND [object_id] = OBJECT_ID(N'[dbo].[password_reset_tokens]')
    )
    BEGIN
        CREATE UNIQUE NONCLUSTERED INDEX [UX_password_reset_tokens_token]
            ON [dbo].[password_reset_tokens]([token]);
    END;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE [name] = N'UX_password_reset_tokens_user'
          AND [object_id] = OBJECT_ID(N'[dbo].[password_reset_tokens]')
    )
    BEGIN
        CREATE UNIQUE NONCLUSTERED INDEX [UX_password_reset_tokens_user]
            ON [dbo].[password_reset_tokens]([user_id]);
    END;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE [name] = N'IX_password_reset_tokens_expires_at'
          AND [object_id] = OBJECT_ID(N'[dbo].[password_reset_tokens]')
    )
    BEGIN
        CREATE NONCLUSTERED INDEX [IX_password_reset_tokens_expires_at]
            ON [dbo].[password_reset_tokens]([expires_at]);
    END;
END;
