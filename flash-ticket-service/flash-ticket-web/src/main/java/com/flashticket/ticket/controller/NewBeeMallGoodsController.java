/**
 * 严肃声明：
 * 开源版本请务必保留此注释头信息，若删除我方将保留所有法律责任追究！
 * 本软件已申请软件著作权，受国家版权局知识产权以及国家计算机软件著作权保护！
 * 可正常分享和学习源码，不得用于违法犯罪活动，违者必究！
 * Copyright (c) 2022 程序员十三 all rights reserved.
 * 版权所有，侵权必究！
 */
package com.flashticket.ticket.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import com.flashticket.common.enums.ServiceResultEnum;
import com.flashticket.common.dto.PageQueryUtil;
import com.flashticket.common.dto.PageResult;
import com.flashticket.common.dto.Result;
import com.flashticket.common.dto.ResultGenerator;
import com.flashticket.common.exception.FlashTicketException;
import com.flashticket.common.pojo.MallUserToken;
import com.flashticket.common.util.BeanUtil;
import com.flashticket.ticket.config.annotation.TokenToMallUser;
import com.flashticket.ticket.controller.vo.FlashTicketGoodsDetailVO;
import com.flashticket.ticket.controller.vo.FlashTicketSearchGoodsVO;
import com.flashticket.ticket.entity.FlashTicketGoods;
import com.flashticket.ticket.service.FlashTicketTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Api(value = "v1", tags = "新蜂商城商品相关接口")
@RequestMapping("/goods/mall")
public class FlashTicketGoodsController {

    private static final Logger logger = LoggerFactory.getLogger(FlashTicketGoodsController.class);

    @Resource
    private FlashTicketTicketService newBeeMallGoodsService;

    @GetMapping("/test1")
    public Result<String> test1() throws BindException {
        throw new BindException(1,"BindException");
    }

    @GetMapping("/test2")
    public Result<String> test2() throws FlashTicketException {
        FlashTicketException.fail("FlashTicketException");
        return ResultGenerator.genSuccessResult("test2");
    }

    @GetMapping("/test3")
    public Result<String> test3() throws Exception {
        int i=1/0;
        return ResultGenerator.genSuccessResult("test2");
    }

    @GetMapping("/search")
    @ApiOperation(value = "商品搜索接口", notes = "根据关键字和分类id进行搜索")
    public Result<PageResult<List<FlashTicketSearchGoodsVO>>> search(@RequestParam(required = false) @ApiParam(value = "搜索关键字") String keyword,
                                                                    @RequestParam(required = false) @ApiParam(value = "分类id") Long goodsCategoryId,
                                                                    @RequestParam(required = false) @ApiParam(value = "orderBy") String orderBy,
                                                                    @RequestParam(required = false) @ApiParam(value = "页码") Integer pageNumber,
                                                                    @TokenToMallUser MallUserToken loginMallUserToken) {
        
        logger.info("goods search api,keyword={},goodsCategoryId={},orderBy={},pageNumber={},userId={}", keyword, goodsCategoryId, orderBy, pageNumber, loginMallUserToken.getUserId());

        Map params = new HashMap(8);
        //两个搜索参数都为空，直接返回异常
        if (goodsCategoryId == null && !StringUtils.hasText(keyword)) {
            FlashTicketException.fail("非法的搜索参数");
        }
        if (pageNumber == null || pageNumber < 1) {
            pageNumber = 1;
        }
        params.put("goodsCategoryId", goodsCategoryId);
        params.put("page", pageNumber);
        params.put("limit", 10);
        //对keyword做过滤 去掉空格
        if (StringUtils.hasText(keyword)) {
            params.put("keyword", keyword);
        }
        if (StringUtils.hasText(orderBy)) {
            params.put("orderBy", orderBy);
        }
        //搜索上架状态下的商品
        params.put("goodsSellStatus", 0);
        //封装商品数据
        PageQueryUtil pageUtil = new PageQueryUtil(params);
        return ResultGenerator.genSuccessResult(newBeeMallGoodsService.searchFlashTicketGoods(pageUtil));
    }

    @GetMapping("/detail/{goodsId}")
    @ApiOperation(value = "商品详情接口", notes = "传参为商品id")
    public Result<FlashTicketGoodsDetailVO> goodsDetail(@ApiParam(value = "商品id") @PathVariable("goodsId") Long goodsId, @TokenToMallUser MallUserToken loginMallUserToken) {
        logger.info("goods detail api,goodsId={},userId={}", goodsId, loginMallUserToken.getUserId());
        if (goodsId < 1) {
            return ResultGenerator.genFailResult("参数异常");
        }
        FlashTicketGoods goods = newBeeMallGoodsService.getFlashTicketGoodsById(goodsId);
        if (0 != goods.getGoodsSellStatus()) {
            FlashTicketException.fail(ServiceResultEnum.GOODS_PUT_DOWN.getResult());
        }
        FlashTicketGoodsDetailVO goodsDetailVO = new FlashTicketGoodsDetailVO();
        BeanUtil.copyProperties(goods, goodsDetailVO);
        goodsDetailVO.setGoodsCarouselList(goods.getGoodsCarousel().split(","));
        return ResultGenerator.genSuccessResult(goodsDetailVO);
    }

}
