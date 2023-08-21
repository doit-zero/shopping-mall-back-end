package com.supercoding.shoppingmallbackend.controller;

import com.supercoding.shoppingmallbackend.common.CommonResponse;
import com.supercoding.shoppingmallbackend.common.Error.CustomException;
import com.supercoding.shoppingmallbackend.common.Error.domain.CommonErrorCode;
import com.supercoding.shoppingmallbackend.dto.response.ScrapResponse;
import com.supercoding.shoppingmallbackend.security.AuthHolder;
import com.supercoding.shoppingmallbackend.service.ScrapService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Api(tags = "찜 목록 API")
public class ScrapController {

    private final ScrapService scrapService;

    @GetMapping()
    @ApiOperation(value = "찜한 상품 전체 조회", notes = "찜 목록 전체를 조회합니다.")
    public CommonResponse<List<ScrapResponse>> getAllScrap() {
        Long profileId = AuthHolder.getUserIdx();
        return scrapService.getAllScrap(profileId);
    }

    @PostMapping("query")
    @ApiOperation(value = "찜하기", notes = "찜 목록에 추가합니다.")
    public CommonResponse<List<ScrapResponse>> addScrap(@RequestParam("productId") Set<String> productIds) {
        Long profileId = AuthHolder.getUserIdx();
        try {
            Set<Long> productIdSet = productIds.stream().map(Long::parseLong).collect(Collectors.toSet());
            return scrapService.addScrap(profileId, productIdSet);
        } catch (NumberFormatException e) {
            throw new CustomException(CommonErrorCode.INVALID_QUERY_PARAMETER);
        }
    }
}
