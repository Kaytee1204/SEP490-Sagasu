package com.example.base.service.approver;

import com.example.base.dto.PageResponse;
import com.example.base.dto.receipt.*;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.mapper.ReceiptMapper;
import com.example.base.repository.CategoryRepository;
import com.example.base.repository.ItemReceiptRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.post.ReceiptPostPublisher;
import com.example.base.service.receipt.ReceiptLocationService;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import com.example.base.storage.ImageValidator.ValidatedImage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApproverIntakeServiceTest {

    @Mock ItemReceiptRepository receipts;
    @Mock CategoryRepository categories;
    @Mock ImageStorage storage;
    @Mock ImageValidator validator;
    @Mock PlatformTransactionManager transactionManager;
    @Mock EntityManager entityManager;
    @Mock ReceiptLocationService locations;
    @Mock ReceiptMapper mapper;
    @Mock ReceiptPostPublisher publisher;

    @InjectMocks
    ApproverIntakeService service;

    Account approver;
    Account regularUser;
    Category activeCat;
    StorageLocation activeLoc;
    OffsetDateTime past;
    OffsetDateTime nowT;

    @BeforeEach
    void setUp() {
        approver = account(2L, AccountRole.APPROVER, AccountStatus.ACTIVE, "Approver 2");
        regularUser = account(3L, AccountRole.USER, AccountStatus.ACTIVE, "User 3");

        activeCat = new Category();
        ReflectionTestUtils.setField(activeCat, "categoryId", 1L);
        activeCat.setName("Ví");
        activeCat.setActive(true);

        activeLoc = StorageLocation.create("K3", "Kệ 3", null);
        ReflectionTestUtils.setField(activeLoc, "id", 3L);

        past = OffsetDateTime.now().minusDays(1);
        nowT = OffsetDateTime.now().minusMinutes(5);

        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(entityManager.find(eq(Account.class), eq(2L), eq(LockModeType.PESSIMISTIC_READ))).thenReturn(approver);
        when(categories.existsByCategoryIdAndActiveTrue(1L)).thenReturn(true);
        when(categories.findActiveForPostCreation(1L)).thenReturn(Optional.of(activeCat));
        when(locations.resolve(eq(3L), any())).thenReturn(activeLoc);
        when(storage.readUrl(any(), any())).thenAnswer(inv -> "url:" + inv.getArgument(0));
        when(mapper.detail(any(ItemReceipt.class))).thenAnswer(inv -> toDto(inv.getArgument(0)));
    }

    private Account account(long id, AccountRole role, AccountStatus status, String name) {
        Account a = new Account();
        a.setId(id);
        a.setRole(role);
        a.setStatus(status);
        a.setFullName(name);
        a.setEmail("u" + id + "@gmail.com");
        a.setPasswordHash("hash");
        return a;
    }

    private CustomUserDetails ud(Account a) {
        return new CustomUserDetails(a);
    }

    private SaveReceiptRequest saveReq(List<Long> keptIds, OffsetDateTime expectedUpdatedAt,
                                       Long catId, Long locId, OffsetDateTime foundAt) {
        return new SaveReceiptRequest(catId, "Ví đen", "Mô tả ví đen", "Còn mới",
                "Sảnh A", foundAt, "CMND bên trong", "Ghi chú", keptIds, expectedUpdatedAt, locId);
    }

    private MultipartFile mockFile(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", new byte[]{1, 2, 3});
    }

    private List<ValidatedImage> validImages(int count, String ext) {
        List<ValidatedImage> list = new ArrayList<>();
        String mime = "png".equals(ext) ? "image/png" : "image/jpeg";
        for (int i = 0; i < count; i++) {
            list.add(new ValidatedImage(new byte[]{1, 2, 3}, mime, ext, 100, 100, "hash" + i));
        }
        return list;
    }

    private ItemReceipt draftReceipt(long id, int imgCount) {
        ItemReceipt r = ItemReceipt.walkInDraft("REC-ABC", approver);
        ReflectionTestUtils.setField(r, "id", id);
        r.edit(activeCat, "Ví đen", "Mô tả", "Tốt", "Cổng A", past, "ID1", "Note");
        r.assignLocation(activeLoc);
        ReflectionTestUtils.setField(r, "updatedAt", nowT);
        for (int i = 0; i < imgCount; i++) {
            ItemReceiptImage img = new ItemReceiptImage("receipts/2/img" + (i + 1) + ".jpg", i, "image/jpeg", 10L);
            ReflectionTestUtils.setField(img, "id", (long) (i + 1));
            r.addImage(img);
        }
        return r;
    }

    private ReceiptDto toDto(ItemReceipt r) {
        return new ReceiptDto(r.getId(), r.getReceiptCode(), r.getStatus(), r.getReviewStatus(),
                r.getCategory() != null ? r.getCategory().getCategoryId() : null,
                r.getCategory() != null ? r.getCategory().getName() : null,
                r.getCategory() != null && r.getCategory().isActive(),
                r.getTitle(), r.getDescription(), r.getConditionAtReceipt(), r.getFoundLocation(), r.getFoundAt(),
                r.getPrivateIdentifiers(), r.getNote(), r.getRecordedAt(), r.getUpdatedAt(), r.getSubmittedAt(),
                null, null, null, List.of(), r.getSource(), "Approver 2",
                r.getStorageLocation() != null ? r.getStorageLocation().getId() : null,
                r.getStorageLocation() != null ? r.getStorageLocation().getName() : null,
                r.getStorageLocation() != null && r.getStorageLocation().isActive(),
                r.getReceivedAt(), null, null, r.getPostId(), null, r.canProcess());
    }

    @Test
    void utc250_create_validWithTwoImages_createsDraftAndUploads() {
        List<MultipartFile> files = List.of(mockFile("1.jpg"), mockFile("2.jpg"));
        when(validator.validate(files)).thenReturn(validImages(2, "jpg"));
        when(receipts.saveAndFlush(any(ItemReceipt.class))).thenAnswer(inv -> {
            ItemReceipt r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 10L);
            return r;
        });

        ReceiptDto res = service.create(saveReq(List.of(), null, 1L, 3L, past), files, ud(approver));

        assertThat(res.receiptCode()).matches("^REC-[0-9A-F]{32}$");
        verify(storage, times(2)).put(argThat(k -> k.startsWith("receipts/2/") && k.endsWith(".jpg")), any(), eq("image/jpeg"));
    }

    @Test
    void utc251_create_nonEmptyKeptImageIds_throwsBadRequest() {
        SaveReceiptRequest req = saveReq(List.of(5L), null, 1L, 3L, past);

        assertThatThrownBy(() -> service.create(req, List.of(mockFile("1.jpg")), ud(approver)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException app = (AppException) ex;
                    assertThat(app.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(app.getMessage()).contains("Danh sách ảnh giữ lại không hợp lệ");
                });
    }

    @Test
    void utc252_create_futureFoundAt_throwsBadRequest() {
        SaveReceiptRequest req = saveReq(List.of(), null, 1L, 3L, OffsetDateTime.now().plusDays(1));

        assertThatThrownBy(() -> service.create(req, List.of(mockFile("1.jpg")), ud(approver)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException app = (AppException) ex;
                    assertThat(app.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(app.getMessage()).isEqualTo("Thời gian nhặt không được ở tương lai");
                });
    }

    @Test
    void utc253_create_inactiveCategory_throwsPostCategoryUnavailable() {
        when(categories.existsByCategoryIdAndActiveTrue(9L)).thenReturn(false);
        SaveReceiptRequest req = saveReq(List.of(), null, 9L, 3L, past);

        assertThatThrownBy(() -> service.create(req, List.of(mockFile("1.jpg")), ud(approver)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.POST_CATEGORY_UNAVAILABLE);
    }

    @Test
    void utc254_create_zeroImages_throwsBadRequest() {
        when(validator.validate(List.of())).thenReturn(List.of());
        SaveReceiptRequest req = saveReq(List.of(), null, 1L, 3L, past);

        assertThatThrownBy(() -> service.create(req, List.of(), ud(approver)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException app = (AppException) ex;
                    assertThat(app.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(app.getMessage()).isEqualTo("Hồ sơ cần từ 1 đến 5 ảnh thực tế");
                });
    }

    @Test
    void utc255_create_oneJpg_accepted() {
        List<MultipartFile> files = List.of(mockFile("1.jpg"));
        when(validator.validate(files)).thenReturn(validImages(1, "jpg"));
        when(receipts.saveAndFlush(any(ItemReceipt.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceiptDto res = service.create(saveReq(List.of(), null, 1L, 3L, past), files, ud(approver));

        assertThat(res.receiptCode()).startsWith("REC-");
    }

    @Test
    void utc256_create_sixImages_throwsImageCountExceeded() {
        List<MultipartFile> files = Collections.nCopies(6, mockFile("1.jpg"));
        when(validator.validate(files)).thenThrow(new AppException(ErrorCode.IMAGE_COUNT_EXCEEDED));

        assertThatThrownBy(() -> service.create(saveReq(List.of(), null, 1L, 3L, past), files, ud(approver)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_COUNT_EXCEEDED);
    }

    @Test
    void utc257_create_inactiveLocation_throwsBadRequestAndCleansUpUploads() {
        List<MultipartFile> files = List.of(mockFile("1.jpg"));
        when(validator.validate(files)).thenReturn(validImages(1, "jpg"));
        when(locations.resolve(eq(3L), any())).thenThrow(
                new AppException(ErrorCode.BAD_REQUEST, "Vị trí lưu giữ không tồn tại hoặc đã ngừng sử dụng. Vui lòng tải lại danh sách."));

        assertThatThrownBy(() -> service.create(saveReq(List.of(), null, 1L, 3L, past), files, ud(approver)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException app = (AppException) ex;
                    assertThat(app.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(app.getMessage()).contains("Vị trí lưu giữ không tồn tại hoặc đã ngừng sử dụng");
                });
        verify(storage).delete(anyString());
    }

    @Test
    void utc258_create_dbSaveFailsAfterUpload_rethrowsAndCleansUpKeys() {
        List<MultipartFile> files = List.of(mockFile("1.jpg"));
        when(validator.validate(files)).thenReturn(validImages(1, "jpg"));
        when(receipts.saveAndFlush(any())).thenThrow(new RuntimeException("db error"));

        assertThatThrownBy(() -> service.create(saveReq(List.of(), null, 1L, 3L, past), files, ud(approver)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db error");

        verify(storage).delete(argThat(k -> k.startsWith("receipts/2/")));
    }

    @Test
    void utc259_submit_validDraft_publishesPostAndMarksReceived() {
        ItemReceipt r = draftReceipt(3L, 2);
        when(receipts.lockIntake(3L)).thenReturn(Optional.of(r));
        Post published = Post.receivedByOffice(approver, activeCat, r.getTitle(), r.getDescription(), r.getFoundLocation(), past);
        ReflectionTestUtils.setField(published, "id", 99L);
        when(publisher.publishFromReceipt(r, approver)).thenReturn(published);

        ReceiptDto res = service.submit(3L, new SubmitReceiptRequest(nowT), ud(approver));

        assertThat(res.postId()).isEqualTo(99L);
        assertThat(r.getStatus()).isEqualTo("IN_STORAGE");
        assertThat(r.getReviewStatus()).isEqualTo("ACCEPTED");
        verify(publisher).publishFromReceipt(r, approver);
    }

    @Test
    void utc260_submit_alreadyPublished_returnsIdempotently() {
        ItemReceipt r = draftReceipt(3L, 2);
        r.receiveAndPublish(approver, 99L);
        when(receipts.lockIntake(3L)).thenReturn(Optional.of(r));

        ReceiptDto res = service.submit(3L, new SubmitReceiptRequest(OffsetDateTime.now().minusHours(2)), ud(approver));

        assertThat(res.postId()).isEqualTo(99L);
        verifyNoInteractions(publisher);
    }

    @Test
    void utc261_submit_blankConditionAtReceipt_throwsBadRequest() {
        ItemReceipt r = draftReceipt(3L, 2);
        ReflectionTestUtils.setField(r, "conditionAtReceipt", "   ");
        when(receipts.lockIntake(3L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.submit(3L, new SubmitReceiptRequest(nowT), ud(approver)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException app = (AppException) ex;
                    assertThat(app.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(app.getMessage()).isEqualTo("Vui lòng hoàn thiện thông tin và tình trạng đồ vật trước khi tiếp nhận");
                });
    }

    @Test
    void utc262_submit_nullStorageLocation_throwsBadRequest() {
        ItemReceipt r = draftReceipt(3L, 2);
        ReflectionTestUtils.setField(r, "storageLocation", null);
        when(receipts.lockIntake(3L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.submit(3L, new SubmitReceiptRequest(nowT), ud(approver)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException app = (AppException) ex;
                    assertThat(app.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(app.getMessage()).isEqualTo("Chọn nơi lưu trữ trước khi xác nhận tiếp nhận.");
                });
    }

    @Test
    void utc263_submit_allImagesSensitive_throwsBadRequest() {
        ItemReceipt r = draftReceipt(3L, 2);
        for (ItemReceiptImage img : r.getImages()) {
            ReflectionTestUtils.setField(img, "sensitive", true);
        }
        when(receipts.lockIntake(3L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.submit(3L, new SubmitReceiptRequest(nowT), ud(approver)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException app = (AppException) ex;
                    assertThat(app.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(app.getMessage()).isEqualTo("Cần ít nhất một ảnh công khai để đăng bài.");
                });
    }

    @Test
    void utc264_submit_categoryDeactivatedMeanwhile_throwsPostCategoryUnavailable() {
        ItemReceipt r = draftReceipt(3L, 2);
        when(receipts.lockIntake(3L)).thenReturn(Optional.of(r));
        when(categories.findActiveForPostCreation(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(3L, new SubmitReceiptRequest(nowT), ud(approver)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.POST_CATEGORY_UNAVAILABLE);
    }

    @Test
    void utc265_submit_approverSuspendedInDb_throwsForbidden() {
        Account suspendedInDb = account(2L, AccountRole.APPROVER, AccountStatus.SUSPENDED, "Approver 2");
        when(entityManager.find(eq(Account.class), eq(2L), eq(LockModeType.PESSIMISTIC_READ))).thenReturn(suspendedInDb);

        assertThatThrownBy(() -> service.submit(3L, new SubmitReceiptRequest(nowT), ud(approver)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}

