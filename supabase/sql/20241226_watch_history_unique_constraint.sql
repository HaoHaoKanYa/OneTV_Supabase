-- 观看历史表唯一性约束迁移
-- 添加唯一性约束防止重复记录插入
-- Migration: Add unique constraint to watch_history table to prevent duplicate records

-- 步骤1: 清理重复记录
DO $$
DECLARE
    duplicate_count INTEGER;
    deleted_count INTEGER;
BEGIN
    -- 统计重复记录组数
    SELECT COUNT(*) INTO duplicate_count
    FROM (
        SELECT user_id, channel_name, channel_url, watch_start, COUNT(*) as cnt
        FROM public.watch_history
        GROUP BY user_id, channel_name, channel_url, watch_start
        HAVING COUNT(*) > 1
    ) duplicates;

    RAISE NOTICE '发现 % 组重复记录', duplicate_count;

    -- 如果存在重复记录，进行清理
    IF duplicate_count > 0 THEN
        RAISE NOTICE '开始清理重复记录...';

        -- 使用ROW_NUMBER()来标识重复记录，保留每组中的第一条记录
        WITH duplicate_records AS (
            SELECT id,
                   ROW_NUMBER() OVER (
                       PARTITION BY user_id, channel_name, channel_url, watch_start
                       ORDER BY created_at ASC, id::text ASC
                   ) as row_num
            FROM public.watch_history
        )
        DELETE FROM public.watch_history
        WHERE id IN (
            SELECT id
            FROM duplicate_records
            WHERE row_num > 1
        );

        GET DIAGNOSTICS deleted_count = ROW_COUNT;
        RAISE NOTICE '已清理 % 条重复记录', deleted_count;
    ELSE
        RAISE NOTICE '没有发现重复记录';
    END IF;
END $$;

-- 步骤2: 添加唯一性约束
DO $$
BEGIN
    -- 检查约束是否已存在
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'watch_history_unique_record'
        AND table_name = 'watch_history'
        AND table_schema = 'public'
    ) THEN
        ALTER TABLE public.watch_history
        ADD CONSTRAINT watch_history_unique_record
        UNIQUE (user_id, channel_name, channel_url, watch_start);

        RAISE NOTICE '唯一性约束添加成功';
    ELSE
        RAISE NOTICE '唯一性约束已存在，跳过添加';
    END IF;
END $$;

-- 步骤3: 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS watch_history_unique_lookup_idx
ON public.watch_history(user_id, channel_name, channel_url, watch_start);

-- 步骤4: 添加约束注释
DO $$
BEGIN
    -- 检查约束是否存在后再添加注释
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'watch_history_unique_record'
        AND table_name = 'watch_history'
        AND table_schema = 'public'
    ) THEN
        EXECUTE 'COMMENT ON CONSTRAINT watch_history_unique_record ON public.watch_history IS ''防止同一用户在相同时间观看相同频道的重复记录''';
        RAISE NOTICE '约束注释添加成功';
    END IF;
END $$;

-- 步骤5: 验证约束添加结果
DO $$
DECLARE
    constraint_exists BOOLEAN;
    index_exists BOOLEAN;
BEGIN
    -- 检查约束是否成功添加
    SELECT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'watch_history_unique_record'
        AND table_name = 'watch_history'
        AND table_schema = 'public'
    ) INTO constraint_exists;

    -- 检查索引是否成功创建
    SELECT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE indexname = 'watch_history_unique_lookup_idx'
        AND tablename = 'watch_history'
        AND schemaname = 'public'
    ) INTO index_exists;

    IF constraint_exists AND index_exists THEN
        RAISE NOTICE '✓ 观看历史表唯一性约束迁移完成';
        RAISE NOTICE '  - 唯一性约束: watch_history_unique_record';
        RAISE NOTICE '  - 查询索引: watch_history_unique_lookup_idx';
        RAISE NOTICE '  - 约束字段: (user_id, channel_name, channel_url, watch_start)';
    ELSE
        RAISE WARNING '⚠ 迁移可能未完全成功，请检查约束和索引状态';
    END IF;
END $$;
