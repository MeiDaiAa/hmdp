---
--- Created by meida.
--- DateTime: 2025/11/10 11:40
---
local vocherId = ARGV[1]
local userID = ARGV[2]

local stockKey = "seckill:stock:" .. vocherId
local orderKey = "seckill:order:" .. vocherId

if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end
if (redis.call('sismember', orderKey, userID) == 1) then
    return 2
end

redis.call('sadd', orderKey, userID)
redis.call('incrby', stockKey, -1)
return 0;
