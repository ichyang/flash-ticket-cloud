/**
 * 严肃声明：
 * 开源版本请务必保留此注释头信息，若删除我方将保留所有法律责任追究！
 * 本系统已申请软件著作权，受国家版权局知识产权以及国家计算机软件著作权保护！
 * 可正常分享和学习源码，不得用于违法犯罪活动，违者必究！
 * Copyright (c) 2022 程序员十三 all rights reserved.
 * 版权所有，侵权必究！
 */
package com.flashticket.ticket.openfeign;

import com.flashticket.common.dto.Result;
import com.flashticket.ticket.dto.FlashTicketGoodsDTO;
import com.flashticket.ticket.dto.UpdateStockNumDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "newbee-mall-cloud-goods-service", path = "/goods")
public interface FlashTicketGoodsServiceFeign {

    @GetMapping(value = "/admin/goodsDetail")
    Result<FlashTicketGoodsDTO> getGoodsDetail(@RequestParam(value = "goodsId") Long goodsId);

    @GetMapping(value = "/admin/listByGoodsIds")
    Result<List<FlashTicketGoodsDTO>> listByGoodsIds(@RequestParam(value = "goodsIds") List<Long> goodsIds);

    @PutMapping(value = "/admin/updateStock")
    Result<Boolean> updateStock(@RequestBody UpdateStockNumDTO updateStockNumDTO);
}
