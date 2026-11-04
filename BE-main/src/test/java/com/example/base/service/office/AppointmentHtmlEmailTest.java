package com.example.base.service.office;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Multipart;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.OffsetDateTime;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppointmentHtmlEmailTest {
    @Test void sendsUtf8HtmlAndPlainTextWithEscapedFieldsAndNoLoginLinks() throws Exception {
        var sender=mock(JavaMailSender.class); var mime=new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);
        var service=new AppointmentEmailService(sender,"office@example.test","https://sagasu.example.test");
        var token=InvitationTokens.create();
        assertTrue(service.send(new AppointmentEmailService.Mail(1L,"user@example.test","An <script>","Wallet & keys",OffsetDateTime.now().plusDays(2),"A101","<img src=x>"),token,null));
        mime.saveChanges(); var text=parts(mime);
        assertTrue(text.contains("text/plain")); assertTrue(text.contains("text/html"));
        assertTrue(text.contains("&lt;script&gt;")); assertTrue(text.contains("&lt;img src=x&gt;"));
        assertTrue(text.contains("#token="+token)); assertTrue(text.contains("decision=CONFIRM")); assertTrue(text.contains("decision=DECLINE"));
        assertFalse(text.contains("/dang-nhap")); verify(sender).send(mime);
        String output=System.getProperty("office.email.preview");
        if(output!=null) java.nio.file.Files.writeString(java.nio.file.Path.of(output),htmlPart(mime));
    }
    private String htmlPart(jakarta.mail.Part part) throws Exception {
        if(part.isMimeType("text/html")) return (String)part.getContent();
        if(part.getContent() instanceof Multipart multi) for(int i=0;i<multi.getCount();i++) { String found=htmlPart(multi.getBodyPart(i)); if(!found.isEmpty()) return found; }
        return "";
    }
    private String parts(jakarta.mail.Part part) throws Exception {
        String result=part.getContentType();
        if(part.getContent() instanceof Multipart multi) for(int i=0;i<multi.getCount();i++) result+=parts(multi.getBodyPart(i));
        else result+=part.getContent(); return result;
    }
}
