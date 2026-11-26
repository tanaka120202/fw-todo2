package com.example.todoapp2;

import java.nio.charset.StandardCharsets;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/todos/{todoId}/attachments")
public class TodoAttachmentController {
    private final TodoAttachmentService attachments;

    public TodoAttachmentController(TodoAttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping
    public String list(@PathVariable long todoId, @AuthenticationPrincipal AccountPrincipal actor, Model model) {
        model.addAttribute("todoId", todoId);
        model.addAttribute("attachments", attachments.list(todoId, actor));
        return "attachments";
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String upload(@PathVariable long todoId, @AuthenticationPrincipal AccountPrincipal actor,
                         @RequestParam MultipartFile file, RedirectAttributes redirect) {
        attachments.upload(todoId, actor, file);
        redirect.addFlashAttribute("attachmentMessage", "attachment.created");
        return "redirect:/todos/{todoId}/attachments";
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable long todoId, @PathVariable long attachmentId,
                                           @AuthenticationPrincipal AccountPrincipal actor) {
        var download = attachments.download(todoId, attachmentId, actor);
        var disposition = ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }

    @PostMapping("/{attachmentId}/delete")
    public String delete(@PathVariable long todoId, @PathVariable long attachmentId,
                         @AuthenticationPrincipal AccountPrincipal actor, RedirectAttributes redirect) {
        attachments.delete(todoId, attachmentId, actor);
        redirect.addFlashAttribute("attachmentMessage", "attachment.deleted");
        return "redirect:/todos/{todoId}/attachments";
    }
}
