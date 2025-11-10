---
--- Created by meida.
--- DateTime: 2025/11/10 10:25
---

-- 获取reids中的值，如果匹配则删除
if (redis.call('get', KEYS[1]) == ARGV[1]) then
    return redis.call('del', KEYS[1]);
end
return 0;