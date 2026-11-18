package com.example.base.service.report;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

class ReportBatchServiceTest {
  @Test void validatesEntireSelectionBeforeApplyingConclusions() {
    var reviews=mock(ReportReviewService.class);
    when(reviews.lock(1L)).thenReturn(Map.of("version",1L,"post_id",10L,"kind","GENERAL","reason_code","SPAM"));
    when(reviews.lock(2L)).thenReturn(Map.of("version",1L,"post_id",10L,"kind","GENERAL","reason_code","FRAUD"));
    assertThrows(RuntimeException.class,()->new ReportBatchService(reviews).conclude(new ReportBatchService.Request(
      List.of(new ReportBatchService.Entry(1L,1L),new ReportBatchService.Entry(2L,1L)),"RESOLVE","Done","Evidence",true),null));
    verify(reviews,never()).act(anyLong(),any(),any());
  }
  @Test void sameObjectDoesNotMeanSameIssue() {
    var a = Map.<String,Object>of("post_id",1L,"kind","GENERAL","reason_code","SPAM");
    assertTrue(ReportBatchService.sameIssue(a,new HashMap<>(a)));
    assertFalse(ReportBatchService.sameIssue(a,Map.of("post_id",1L,"kind","GENERAL","reason_code","FRAUD")));
    assertFalse(ReportBatchService.sameIssue(a,Map.of("post_id",2L,"kind","GENERAL","reason_code","SPAM")));
  }
  @Test void staleSelectionNeverStartsAnyConclusion() {
    var reviews=mock(ReportReviewService.class);
    when(reviews.lock(1L)).thenReturn(Map.of("version",2L));
    var service=new ReportBatchService(reviews);
    assertThrows(RuntimeException.class,()->service.conclude(new ReportBatchService.Request(
      List.of(new ReportBatchService.Entry(1L,1L)),"RESOLVE","Done","Evidence",true),null));
    verify(reviews,never()).act(anyLong(),any(),any());
  }
  @Test void rejectsSanctionsAndUnconfirmedBatch() {
    var reviews=mock(ReportReviewService.class);
    var service=new ReportBatchService(reviews);
    assertThrows(RuntimeException.class,()->service.conclude(new ReportBatchService.Request(
      List.of(new ReportBatchService.Entry(1L,1L)),"HIDE_POST","Done","Evidence",true),null));
    assertThrows(RuntimeException.class,()->service.conclude(new ReportBatchService.Request(
      List.of(new ReportBatchService.Entry(1L,1L)),"RESOLVE","Done","Evidence",false),null));
    verifyNoInteractions(reviews);
  }
}
