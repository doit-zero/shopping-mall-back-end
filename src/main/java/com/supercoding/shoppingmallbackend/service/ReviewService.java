package com.supercoding.shoppingmallbackend.service;

import com.supercoding.shoppingmallbackend.common.CommonResponse;
import com.supercoding.shoppingmallbackend.common.Error.CustomException;
import com.supercoding.shoppingmallbackend.common.Error.domain.*;
import com.supercoding.shoppingmallbackend.common.util.ApiUtils;
import com.supercoding.shoppingmallbackend.common.util.FilePath;
import com.supercoding.shoppingmallbackend.common.util.JpaUtils;
import com.supercoding.shoppingmallbackend.common.util.PaginationBuilder;
import com.supercoding.shoppingmallbackend.dto.response.PaginationResponse;
import com.supercoding.shoppingmallbackend.dto.response.ReviewResponse;
import com.supercoding.shoppingmallbackend.entity.Consumer;
import com.supercoding.shoppingmallbackend.entity.Product;
import com.supercoding.shoppingmallbackend.entity.Review;
import com.supercoding.shoppingmallbackend.repository.ConsumerRepository;
import com.supercoding.shoppingmallbackend.repository.ProductRepository;
import com.supercoding.shoppingmallbackend.repository.ReviewRepository;
import com.supercoding.shoppingmallbackend.security.AuthHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ConsumerRepository consumerRepository;
    private final ProductRepository productRepository;
    private final AwsS3Service awsS3Service;

    @Cacheable(value = "review", key = "'getAllProductReview('+#productId+')'")
    public CommonResponse<List<ReviewResponse>> getAllProductReview(long productId) {
        List<Review> datas = reviewRepository.findAllByProductId(productId);
        List<ReviewResponse> responses = datas.stream().map(ReviewResponse::from).collect(Collectors.toList());
        return ApiUtils.success("상품 리뷰를 성공적으로 조회했습니다.", responses);
    }

    @Cacheable(value = "review", key = "'getPageProductReivew('+#productId+')'")
    public CommonResponse<PaginationResponse<ReviewResponse>> getAllProductREviewWithPagination(long productId, int page, int size) {
        Page<Review> pageData = reviewRepository.findAllByProductIdWithPagination(productId, PageRequest.of(page, size));
        List<ReviewResponse> contents = pageData.getContent().stream().map(ReviewResponse::from).collect(Collectors.toList());
        PaginationResponse<ReviewResponse> response = new PaginationBuilder<ReviewResponse>()
                .hasNext(pageData.hasNext())
                .hasPrivious(pageData.hasPrevious())
                .totalPages(pageData.getTotalPages())
                .contents(contents)
                .build();

        return ApiUtils.success("상품 리뷰를 성공적으로 조회했습니다.", response);
    }

    public CommonResponse<List<ReviewResponse>> getAllMyReview() {
        Consumer consumer = getConsumer();

        List<Review> datas = reviewRepository.findAllByConsumer(consumer);
        List<ReviewResponse> responses = datas.stream().map(ReviewResponse::from).collect(Collectors.toList());

        return ApiUtils.success("내가 작성한 리뷰를 성공적으로 조회했습니다.", responses);
    }

    public CommonResponse<PaginationResponse<ReviewResponse>> getAllMyReviewWithPagination(int page, int size) {
        Consumer consumer = getConsumer();
        Page<Review> dataPage = reviewRepository.findPageByConsumer(consumer, PageRequest.of(page, size));
        List<ReviewResponse> contents = dataPage.getContent().stream().map(ReviewResponse::from).collect(Collectors.toList());
        PaginationResponse<ReviewResponse> response = new PaginationBuilder<ReviewResponse>()
                .contents(contents)
                .hasNext(dataPage.hasNext())
                .hasPrivious(dataPage.hasPrevious())
                .totalPages(dataPage.getTotalPages())
                .build();
        return ApiUtils.success("내가 작성한 리뷰를 성공적으로 조회했습니다.", response);
    }

    @Transactional
    @CacheEvict(value = "review", allEntries = true)
    public CommonResponse<ReviewResponse> createReview(MultipartFile imageFile, Long productId, String content, Double rating) {
        Consumer consumer = getConsumer();
        Product product = productRepository.findProductById(productId).orElseThrow(()->new CustomException(ProductErrorCode.NOTFOUND_PRODUCT));

        Review newData = Review.builder()
                .consumer(consumer)
                .product(product)
                .content(content)
                .rating(rating)
                .build();
        newData.setIsDeleted(false);
        Review savedData = JpaUtils.managedSave(reviewRepository, newData);

        if (imageFile != null) {
            String imageUrl = uploadImageFile(imageFile, String.valueOf(savedData.getId()));
            savedData.setReviewImageUrl(imageUrl);
        }

        ReviewResponse response = ReviewResponse.from(savedData);
        return ApiUtils.success("리뷰를 성공적으로 작성했습니다.", response);
    }

    @Transactional
    @CacheEvict(value = "review", allEntries = true)
    public CommonResponse<ReviewResponse> modifyReview(long reviewId, MultipartFile imageFile, String content, Double rating) {
        Consumer consumer = getConsumer();

        Review data = reviewRepository.findByConsumerAndReviewId(consumer, reviewId).orElseThrow(()->new CustomException(ReviewErrorCode.BAD_ID));
        if (imageFile != null) data.setReviewImageUrl(uploadImageFile(imageFile, String.valueOf(data.getId())));
        else data.setReviewImageUrl(null);

        data.setContent(content);
        data.setRating(rating);

        return ApiUtils.success("리뷰를 성공적으로 수정하였습니다.", ReviewResponse.from(data));
    }

    @Transactional
    @CacheEvict(value = "review", allEntries = true)
    public CommonResponse<List<ReviewResponse>> softDeleteReviews(Set<Long> idSet) {
        Consumer consumer = getConsumer();

        List<Review> datas = reviewRepository.findAllByConsumer(consumer);
        List<ReviewResponse> responses = datas.stream()
                .filter(data->idSet.contains(data.getId()))
                .map(data->{
                    data.setIsDeleted(true);
                    return ReviewResponse.from(data);
                })
                .collect(Collectors.toList());

        return ApiUtils.success("리뷰를 성공적으로 삭제했습니다.", responses);
    }

    private String uploadImageFile(MultipartFile imageFile, String imageId) {
        if (imageFile == null) return null;

        try {
            return awsS3Service.upload(imageFile, FilePath.REVIEW_IMAGE_DIR.getPath()+imageId);
        } catch (IOException e) {
            throw new CustomException(UtilErrorCode.IOE_ERROR);
        }
    }

    private Consumer getConsumer() {
        return consumerRepository.findByProfileId(AuthHolder.getProfileIdx()).orElseThrow(()->new CustomException(ConsumerErrorCode.NOT_FOUND_BY_ID));
    }
}