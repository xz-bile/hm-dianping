package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;

import lombok.Synchronized;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.LocalDateTime;

import javax.annotation.Resource;

import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

  @Resource 
  private ISeckillVoucherService seckillVoucherService;

  @Resource 
  private RedisWorker redisWorker;

  @Override
  public Result seckillVoucher(Long voucherId) {
    //1-查询
    SeckillVoucher voucher = seckillVoucherService.getById(voucherId);;

    //2,判断秒杀是否开始
    if(voucher.getBeginTime().isAfter(LocalDateTime.now())) {
      return Result.fail("秒杀尚未开始");
    }

    //3,判断秒杀是否结束
    if(voucher.getEndTime().isBefore(LocalDateTime.now())) {
      return Result.fail("秒杀已经结束");
    }

    //4,判断库存是否充足
    if(voucher.getStock() < 1) {
      return Result.fail("库存不足");
    }

    Long userId=UserHolder.getUser().getId();
    //
    synchronized(userId.toString().intern()){ //toString比较内存地址，intern比较值
      //获取代理对象（解决内部事务没有生效问题）
      IVoucherOrderService proxy =(IVoucherOrderService) AopContext.currentProxy();
      return proxy.createVoucherORder(voucherId);
    }
  }

  @Transactional 
  public Result createVoucherORder(Long voucherId) { 
    //不在方法上加synchronized：不同用户使用同一把锁（性能下降）。因此给每一个用户添加一把锁。
    //5,一人一单
    Long userId = UserHolder.getUser().getId();

      //5.1查询订单
      int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

      //5.2判断是否存在
      if(count>0){
        return Result.fail("不能重复下单");
      }

      //6,扣减库存（查询同时修改）
      boolean success=seckillVoucherService.update()
        .setSql("stock = stock - 1")
        .eq("voucher_id", voucherId)
        .gt("stock", 0)
        .update();  //乐观锁

      if(!success) {
        return Result.fail("库存不足");
      }
      //7,创建订单
      VoucherOrder voucherOrder = new VoucherOrder();
      //订单ID（唯一：通过redisworker实现）
      long orderId = redisWorker.nextId("order");
      voucherOrder.setId(orderId);

      //用户ID
      voucherOrder.setUserId(userId);

      //代金券ID
      voucherOrder.setVoucherId(voucherId);

      save(voucherOrder);

      //7,返回订单id
      return Result.ok(orderId);
  }

}
