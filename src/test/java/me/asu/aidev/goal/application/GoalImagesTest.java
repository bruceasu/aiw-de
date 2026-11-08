package me.asu.aidev.goal.application;

import java.util.*;
import me.asu.aidev.goal.domain.GoalData.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoalImagesTest {
    private final Draft draft=new Draft("goal","actor","title",1,0,false,new Content("","","deepseek",List.of(),List.of()));
    private byte[] png(int size) {
        byte[] bytes=new byte[size];byte[] header={(byte)137,80,78,71,13,10,26,10};
        System.arraycopy(header,0,bytes,0,header.length);return bytes;
    }
    @Test void onlySelectedOwnedImagesAreFrozenAsExactBytes() {
        var files=mock(GoalAttachments.class);byte[] bytes=png(32);String hash=GoalAttachments.hash(bytes);
        when(files.list("goal","actor")).thenReturn(List.of(new Attachment("one","one.png",hash,"IMAGE_READY","",""),
            new Attachment("other","other.png",hash,"IMAGE_READY","","")));
        when(files.bytes("goal","one","actor")).thenReturn(bytes);
        var frozen=GoalImages.selected(files,draft,List.of("one"));
        assertThat(frozen).hasSize(1);assertThat(frozen.getFirst().sha256()).isEqualTo(hash);
        assertThat(Base64.getDecoder().decode(frozen.getFirst().base64())).containsExactly(bytes);
        verify(files,never()).bytes("goal","other","actor");
        assertThatThrownBy(()->GoalImages.selected(files,draft,List.of("foreign"))).hasMessageContaining("not in this goal");
    }
    @Test void changedAndOversizedImagesCannotEnterARequest() {
        var files=mock(GoalAttachments.class);byte[] bytes=png(300000);String hash=GoalAttachments.hash(bytes);
        when(files.list("goal","actor")).thenReturn(List.of(new Attachment("one","one.png",hash,"IMAGE_READY","",""),
            new Attachment("two","two.png",hash,"IMAGE_READY","","")));
        when(files.bytes("goal","one","actor")).thenReturn(bytes);when(files.bytes("goal","two","actor")).thenReturn(bytes);
        assertThatThrownBy(()->GoalImages.selected(files,draft,List.of("one","two"))).hasMessageContaining("512 KiB");
        when(files.bytes("goal","one","actor")).thenReturn(png(33));
        assertThatThrownBy(()->GoalImages.selected(files,draft,List.of("one"))).hasMessageContaining("changed");
    }
    @Test void historicFrozenRequestsDefaultToNoImagesAndNoOAuthMarker() throws Exception {
        var frozen=new com.fasterxml.jackson.databind.ObjectMapper().readValue("{\"backend\":\"openai\",\"version\":\"v1\",\"profiles\":[]}",GoalTaskPolicy.Frozen.class);
        assertThat(frozen.images()).isEmpty();assertThat(frozen.oauth()).isFalse();
    }
}
