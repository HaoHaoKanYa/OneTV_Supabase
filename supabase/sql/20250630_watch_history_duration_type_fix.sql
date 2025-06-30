-- 观看历史表duration字段类型修复
-- 将duration字段从integer改为bigint以匹配项目中的Long类型
-- Migration: Fix watch_history duration field type to match SupabaseWatchHistoryItem

-- 步骤1: 检查当前字段类型
DO $$
DECLARE
    current_type TEXT;
BEGIN
    SELECT data_type INTO current_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
    AND table_name = 'watch_history'
    AND column_name = 'duration';
    
    RAISE NOTICE '当前duration字段类型: %', current_type;
END $$;

-- 步骤2: 修改字段类型
DO $$
DECLARE
    current_type TEXT;
BEGIN
    -- 获取当前字段类型
    SELECT data_type INTO current_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
    AND table_name = 'watch_history'
    AND column_name = 'duration';
    
    -- 如果当前类型不是bigint，则进行修改
    IF current_type != 'bigint' THEN
        RAISE NOTICE '开始修改duration字段类型从 % 到 bigint', current_type;
        
        -- 修改字段类型
        ALTER TABLE public.watch_history 
        ALTER COLUMN duration TYPE bigint;
        
        RAISE NOTICE '✓ duration字段类型修改完成';
    ELSE
        RAISE NOTICE 'duration字段类型已经是bigint，无需修改';
    END IF;
END $$;

-- 步骤3: 验证修改结果
DO $$
DECLARE
    new_type TEXT;
    record_count INTEGER;
BEGIN
    -- 检查新的字段类型
    SELECT data_type INTO new_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
    AND table_name = 'watch_history'
    AND column_name = 'duration';
    
    -- 检查记录数量
    SELECT COUNT(*) INTO record_count
    FROM public.watch_history;
    
    IF new_type = 'bigint' THEN
        RAISE NOTICE '✓ 观看历史表duration字段类型修复完成';
        RAISE NOTICE '  - 新字段类型: %', new_type;
        RAISE NOTICE '  - 现有记录数: %', record_count;
        RAISE NOTICE '  - 兼容性: 与SupabaseWatchHistoryItem.duration(Long)完全匹配';
    ELSE
        RAISE WARNING '⚠ 字段类型修改可能未成功，当前类型: %', new_type;
    END IF;
END $$;

-- 步骤4: 添加字段注释
COMMENT ON COLUMN public.watch_history.duration IS '观看时长（秒），bigint类型匹配Kotlin Long类型';

-- 步骤5: 检查数据完整性
DO $$
DECLARE
    null_count INTEGER;
    negative_count INTEGER;
    max_duration BIGINT;
BEGIN
    -- 检查空值
    SELECT COUNT(*) INTO null_count
    FROM public.watch_history
    WHERE duration IS NULL;
    
    -- 检查负值
    SELECT COUNT(*) INTO negative_count
    FROM public.watch_history
    WHERE duration < 0;
    
    -- 检查最大值
    SELECT COALESCE(MAX(duration), 0) INTO max_duration
    FROM public.watch_history;
    
    RAISE NOTICE '数据完整性检查:';
    RAISE NOTICE '  - 空值记录数: %', null_count;
    RAISE NOTICE '  - 负值记录数: %', negative_count;
    RAISE NOTICE '  - 最大观看时长: % 秒', max_duration;
    
    IF null_count > 0 OR negative_count > 0 THEN
        RAISE WARNING '⚠ 发现异常数据，建议检查和清理';
    ELSE
        RAISE NOTICE '✓ 数据完整性检查通过';
    END IF;
END $$;
