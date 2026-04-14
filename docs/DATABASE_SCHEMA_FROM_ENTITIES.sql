IF OBJECT_ID('USERS', 'U') IS NULL
BEGIN
    CREATE TABLE USERS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        username NVARCHAR(255) NOT NULL,
        email NVARCHAR(255) NULL,
        password NVARCHAR(255) NOT NULL,
        role NVARCHAR(255) NOT NULL,
        CONSTRAINT UQ_USERS_USERNAME UNIQUE (username),
        CONSTRAINT UQ_USERS_EMAIL UNIQUE (email)
    );
END
GO

IF OBJECT_ID('CATEGORIES', 'U') IS NULL
BEGIN
    CREATE TABLE CATEGORIES (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        name NVARCHAR(255) NOT NULL,
        description NVARCHAR(500) NULL,
        created_at DATETIME NULL,
        updated_at DATETIME NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        deleted BIT NOT NULL CONSTRAINT DF_CATEGORIES_DELETED DEFAULT (0),
        CONSTRAINT UQ_CATEGORIES_NAME UNIQUE (name)
    );
END
GO

IF OBJECT_ID('WAREHOUSE', 'U') IS NULL
BEGIN
    CREATE TABLE WAREHOUSE (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        name NVARCHAR(255) NOT NULL,
        location NVARCHAR(500) NULL,
        active BIT NOT NULL CONSTRAINT DF_WAREHOUSE_ACTIVE DEFAULT (1),
        created_at DATETIME NOT NULL,
        CONSTRAINT UQ_WAREHOUSE_NAME UNIQUE (name)
    );
END
GO

IF OBJECT_ID('WINES', 'U') IS NULL
BEGIN
    CREATE TABLE WINES (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        name NVARCHAR(255) NOT NULL,
        type NVARCHAR(255) NOT NULL,
        year INT NOT NULL,
        price DECIMAL(10,2) NOT NULL,
        description NVARCHAR(1000) NULL,
        country NVARCHAR(100) NULL,
        image_url NVARCHAR(500) NULL,
        category_id BIGINT NULL,
        created_at DATETIME NULL,
        updated_at DATETIME NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        deleted BIT NOT NULL CONSTRAINT DF_WINES_DELETED DEFAULT (0),
        CONSTRAINT FK_WINES_CATEGORY FOREIGN KEY (category_id) REFERENCES CATEGORIES(id)
    );
END
GO

IF OBJECT_ID('CARTS', 'U') IS NULL
BEGIN
    CREATE TABLE CARTS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id BIGINT NULL,
        CONSTRAINT UQ_CARTS_USER_ID UNIQUE (user_id),
        CONSTRAINT FK_CARTS_USER FOREIGN KEY (user_id) REFERENCES USERS(id)
    );
END
GO

IF OBJECT_ID('ORDERS', 'U') IS NULL
BEGIN
    CREATE TABLE ORDERS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id BIGINT NOT NULL,
        order_date DATETIME NOT NULL,
        total_price DECIMAL(10,2) NOT NULL,
        status NVARCHAR(50) NOT NULL,
        payment_status NVARCHAR(50) NOT NULL,
        payment_method NVARCHAR(50) NULL,
        shipping_full_name NVARCHAR(255) NULL,
        shipping_phone NVARCHAR(50) NULL,
        shipping_email NVARCHAR(255) NULL,
        shipping_address NVARCHAR(1000) NULL,
        shipping_latitude DECIMAL(10,2) NULL,
        shipping_longitude DECIMAL(10,2) NULL,
        order_note NVARCHAR(1000) NULL,
        payment_reference NVARCHAR(100) NULL,
        paid_at DATETIME NULL,
        updated_at DATETIME NOT NULL,
        CONSTRAINT FK_ORDERS_USER FOREIGN KEY (user_id) REFERENCES USERS(id)
    );
END
GO

IF OBJECT_ID('SHIPPERS', 'U') IS NULL
BEGIN
    CREATE TABLE SHIPPERS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id BIGINT NOT NULL,
        name NVARCHAR(255) NOT NULL,
        phone NVARCHAR(50) NOT NULL,
        vehicle_type NVARCHAR(100) NULL,
        status NVARCHAR(50) NOT NULL,
        is_available BIT NOT NULL CONSTRAINT DF_SHIPPERS_IS_AVAILABLE DEFAULT (0),
        current_latitude DECIMAL(10,2) NULL,
        current_longitude DECIMAL(10,2) NULL,
        location_updated_at DATETIME NULL,
        max_concurrent_shipments INT NOT NULL CONSTRAINT DF_SHIPPERS_MAX_CONCURRENT DEFAULT (1),
        active_shipment_count INT NOT NULL CONSTRAINT DF_SHIPPERS_ACTIVE_COUNT DEFAULT (0),
        last_assignment_at DATETIME NULL,
        created_at DATETIME NOT NULL,
        updated_at DATETIME NOT NULL,
        CONSTRAINT UQ_SHIPPERS_USER_ID UNIQUE (user_id),
        CONSTRAINT FK_SHIPPERS_USER FOREIGN KEY (user_id) REFERENCES USERS(id)
    );
END
GO

IF OBJECT_ID('INVENTORY', 'U') IS NULL
BEGIN
    CREATE TABLE INVENTORY (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        wine_id BIGINT NOT NULL,
        warehouse_id BIGINT NOT NULL,
        current_quantity INT NOT NULL CONSTRAINT DF_INVENTORY_CURRENT_QTY DEFAULT (0),
        reserved_quantity INT NOT NULL CONSTRAINT DF_INVENTORY_RESERVED_QTY DEFAULT (0),
        reorder_level INT NOT NULL CONSTRAINT DF_INVENTORY_REORDER_LEVEL DEFAULT (10),
        updated_at DATETIME NOT NULL,
        version BIGINT NULL,
        CONSTRAINT UQ_INVENTORY_WINE_WAREHOUSE UNIQUE (wine_id, warehouse_id),
        CONSTRAINT FK_INVENTORY_WINE FOREIGN KEY (wine_id) REFERENCES WINES(id),
        CONSTRAINT FK_INVENTORY_WAREHOUSE FOREIGN KEY (warehouse_id) REFERENCES WAREHOUSE(id)
    );
END
GO

IF OBJECT_ID('STOCK_LOGS', 'U') IS NULL
BEGIN
    CREATE TABLE STOCK_LOGS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        inventory_id BIGINT NOT NULL,
        available_quantity INT NOT NULL,
        message NVARCHAR(500) NULL,
        created_at DATETIME NOT NULL,
        CONSTRAINT FK_STOCK_LOGS_INVENTORY FOREIGN KEY (inventory_id) REFERENCES INVENTORY(id)
    );
END
GO

IF OBJECT_ID('INVENTORY_TRANSACTIONS', 'U') IS NULL
BEGIN
    CREATE TABLE INVENTORY_TRANSACTIONS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        inventory_id BIGINT NOT NULL,
        product_id BIGINT NOT NULL,
        warehouse_id BIGINT NOT NULL,
        quantity INT NOT NULL,
        operation_type NVARCHAR(255) NOT NULL,
        reference_type NVARCHAR(100) NULL,
        reference_id BIGINT NULL,
        user_id BIGINT NULL,
        note NVARCHAR(500) NULL,
        created_at DATETIME NOT NULL,
        created_by NVARCHAR(255) NULL,
        CONSTRAINT FK_INV_TX_INVENTORY FOREIGN KEY (inventory_id) REFERENCES INVENTORY(id),
        CONSTRAINT FK_INV_TX_WINE FOREIGN KEY (product_id) REFERENCES WINES(id),
        CONSTRAINT FK_INV_TX_WAREHOUSE FOREIGN KEY (warehouse_id) REFERENCES WAREHOUSE(id)
    );
END
GO

IF OBJECT_ID('CART_ITEMS', 'U') IS NULL
BEGIN
    CREATE TABLE CART_ITEMS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        cart_id BIGINT NOT NULL,
        wine_id BIGINT NOT NULL,
        quantity INT NOT NULL,
        CONSTRAINT FK_CART_ITEMS_CART FOREIGN KEY (cart_id) REFERENCES CARTS(id),
        CONSTRAINT FK_CART_ITEMS_WINE FOREIGN KEY (wine_id) REFERENCES WINES(id)
    );
END
GO

IF OBJECT_ID('ORDER_ITEMS', 'U') IS NULL
BEGIN
    CREATE TABLE ORDER_ITEMS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        order_id BIGINT NOT NULL,
        wine_id BIGINT NOT NULL,
        quantity INT NOT NULL,
        price DECIMAL(10,2) NOT NULL,
        CONSTRAINT FK_ORDER_ITEMS_ORDER FOREIGN KEY (order_id) REFERENCES ORDERS(id),
        CONSTRAINT FK_ORDER_ITEMS_WINE FOREIGN KEY (wine_id) REFERENCES WINES(id)
    );
END
GO

IF OBJECT_ID('PAYMENTS', 'U') IS NULL
BEGIN
    CREATE TABLE PAYMENTS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        order_id BIGINT NOT NULL,
        method NVARCHAR(50) NOT NULL,
        status NVARCHAR(50) NOT NULL,
        amount DECIMAL(10,2) NOT NULL,
        currency NVARCHAR(10) NOT NULL CONSTRAINT DF_PAYMENTS_CURRENCY DEFAULT (N'VND'),
        payment_reference NVARCHAR(100) NOT NULL,
        gateway_session_id NVARCHAR(150) NULL,
        gateway_response NVARCHAR(2000) NULL,
        created_at DATETIME NOT NULL,
        updated_at DATETIME NOT NULL,
        CONSTRAINT UQ_PAYMENTS_PAYMENT_REFERENCE UNIQUE (payment_reference),
        CONSTRAINT FK_PAYMENTS_ORDER FOREIGN KEY (order_id) REFERENCES ORDERS(id)
    );
END
GO

IF OBJECT_ID('PAYMENT_TRANSACTIONS', 'U') IS NULL
BEGIN
    CREATE TABLE PAYMENT_TRANSACTIONS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        payment_id BIGINT NOT NULL,
        transaction_type NVARCHAR(100) NOT NULL,
        status NVARCHAR(50) NOT NULL,
        payload NVARCHAR(2000) NULL,
        created_at DATETIME NOT NULL,
        CONSTRAINT FK_PAYMENT_TX_PAYMENT FOREIGN KEY (payment_id) REFERENCES PAYMENTS(id)
    );
END
GO

IF OBJECT_ID('REVIEWS', 'U') IS NULL
BEGIN
    CREATE TABLE REVIEWS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        wine_id BIGINT NOT NULL,
        user_id BIGINT NOT NULL,
        rating INT NOT NULL,
        comment NVARCHAR(1000) NULL,
        created_at DATETIME NOT NULL,
        CONSTRAINT FK_REVIEWS_WINE FOREIGN KEY (wine_id) REFERENCES WINES(id),
        CONSTRAINT FK_REVIEWS_USER FOREIGN KEY (user_id) REFERENCES USERS(id)
    );
END
GO

IF OBJECT_ID('PASSWORD_RESET_TOKENS', 'U') IS NULL
BEGIN
    CREATE TABLE PASSWORD_RESET_TOKENS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id BIGINT NOT NULL,
        token NVARCHAR(120) NOT NULL,
        expires_at DATETIME NOT NULL,
        used_at DATETIME NULL,
        created_at DATETIME NOT NULL,
        CONSTRAINT UQ_PASSWORD_RESET_TOKENS_TOKEN UNIQUE (token),
        CONSTRAINT FK_PASSWORD_RESET_TOKENS_USER FOREIGN KEY (user_id) REFERENCES USERS(id)
    );
END
GO

IF OBJECT_ID('SHIPMENTS', 'U') IS NULL
BEGIN
    CREATE TABLE SHIPMENTS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        order_id BIGINT NOT NULL,
        shipper_id BIGINT NULL,
        status NVARCHAR(50) NOT NULL,
        shipping_name NVARCHAR(255) NULL,
        shipping_phone NVARCHAR(50) NULL,
        shipping_email NVARCHAR(255) NULL,
        shipping_address NVARCHAR(1000) NULL,
        shipping_latitude DECIMAL(10,2) NULL,
        shipping_longitude DECIMAL(10,2) NULL,
        otp_code NVARCHAR(6) NULL,
        otp_created_at DATETIME NULL,
        otp_expires_at DATETIME NULL,
        otp_attempt_count INT NOT NULL CONSTRAINT DF_SHIPMENTS_OTP_ATTEMPT DEFAULT (0),
        otp_locked_until DATETIME NULL,
        otp_last_sent_at DATETIME NULL,
        otp_sent_at DATETIME NULL,
        otp_delivery_status NVARCHAR(20) NOT NULL,
        otp_user_id BIGINT NULL,
        otp_verified BIT NOT NULL CONSTRAINT DF_SHIPMENTS_OTP_VERIFIED DEFAULT (0),
        admin_override BIT NOT NULL CONSTRAINT DF_SHIPMENTS_ADMIN_OVERRIDE DEFAULT (0),
        admin_override_reason NVARCHAR(500) NULL,
        failure_note NVARCHAR(500) NULL,
        failure_code NVARCHAR(50) NULL,
        status_reason NVARCHAR(500) NULL,
        estimated_delivery_at DATETIME NULL,
        promised_window_start DATETIME NULL,
        promised_window_end DATETIME NULL,
        delivery_attempt_count INT NOT NULL CONSTRAINT DF_SHIPMENTS_DELIVERY_ATTEMPT DEFAULT (0),
        last_delivery_attempt_at DATETIME NULL,
        next_attempt_at DATETIME NULL,
        assigned_at DATETIME NULL,
        failed_at DATETIME NULL,
        returned_at DATETIME NULL,
        created_at DATETIME NOT NULL,
        updated_at DATETIME NOT NULL,
        picked_up_at DATETIME NULL,
        delivering_at DATETIME NULL,
        completed_at DATETIME NULL,
        CONSTRAINT UQ_SHIPMENTS_ORDER_ID UNIQUE (order_id),
        CONSTRAINT FK_SHIPMENTS_ORDER FOREIGN KEY (order_id) REFERENCES ORDERS(id),
        CONSTRAINT FK_SHIPMENTS_SHIPPER FOREIGN KEY (shipper_id) REFERENCES SHIPPERS(id)
    );
END
GO

IF OBJECT_ID('SHIPMENT_STATUS_HISTORY', 'U') IS NULL
BEGIN
    CREATE TABLE SHIPMENT_STATUS_HISTORY (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        shipment_id BIGINT NOT NULL,
        from_status NVARCHAR(50) NULL,
        to_status NVARCHAR(50) NOT NULL,
        reason NVARCHAR(500) NULL,
        metadata NVARCHAR(2000) NULL,
        actor_user_id BIGINT NULL,
        actor_username NVARCHAR(255) NULL,
        created_at DATETIME NOT NULL,
        CONSTRAINT FK_SHIPMENT_STATUS_HISTORY_SHIPMENT FOREIGN KEY (shipment_id) REFERENCES SHIPMENTS(id)
    );
END
GO

IF OBJECT_ID('SHIPMENT_OTP_AUDIT_LOGS', 'U') IS NULL
BEGIN
    CREATE TABLE SHIPMENT_OTP_AUDIT_LOGS (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        shipment_id BIGINT NOT NULL,
        order_id BIGINT NULL,
        otp_user_id BIGINT NULL,
        actor_user_id BIGINT NULL,
        actor_username NVARCHAR(255) NULL,
        action NVARCHAR(100) NOT NULL,
        status NVARCHAR(50) NOT NULL,
        reason NVARCHAR(1000) NULL,
        metadata NVARCHAR(2000) NULL,
        created_at DATETIME NOT NULL,
        CONSTRAINT FK_SHIPMENT_OTP_AUDIT_SHIPMENT FOREIGN KEY (shipment_id) REFERENCES SHIPMENTS(id)
    );
END
GO
