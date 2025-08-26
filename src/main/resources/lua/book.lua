-- KEYS[1]=open, KEYS[2]=availSet, KEYS[3]=userLock, KEYS[4]=stream
-- ARGV[1]=userId, ARGV[2]=orderId, ARGV[3]=ttlMillis, ARGV[4]=ymd, ARGV[5]=hour
if redis.call('get', KEYS[1]) ~= 'true' then return -4 end
if redis.call('setnx', KEYS[3], 1) == 0 then return -2 end
redis.call('pexpire', KEYS[3], ARGV[3])
local slotId = redis.call('spop', KEYS[2])
if not slotId then return 0 end
redis.call('xadd', KEYS[4], '*', 'orderId', ARGV[2], 'userId', ARGV[1], 'slotId', slotId, 'ymd', ARGV[4], 'hour', ARGV[5])
return tonumber(slotId)
