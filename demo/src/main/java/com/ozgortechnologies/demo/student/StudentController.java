package com.ozgortechnologies.demo.student;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class StudentController {

    private final StudentService studentService;
    private final Sinks.Many<Student> sink = Sinks.many().multicast().onBackpressureBuffer();

    @PostMapping
    public Mono<Student> save(@RequestBody Student student) {
        return studentService.save(student)
                .doOnNext(sink::tryEmitNext); // Yeni öğrenci eklendiğinde SSE'ye yayınla
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable Integer id) {
        return studentService.findById(id)
                .map(student -> {
                    student.setFirstname("DELETED"); // Opsiyonel: Silindiğini göstermek için
                    student.setNumber(-1);         // Opsiyonel: Yaşı anlamlı olmayan bir değere çek
                    return student;
                })
                .doOnNext(sink::tryEmitNext) // Silinen öğrenci bilgilerini SSE'ye yayınla
                .then(studentService.deleteById(id));
    }


    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<Student> findAll() {
        return studentService.findAll();
    }

    @GetMapping(value = "/updates", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Student> streamUpdates() {
        return sink.asFlux()
                .doOnSubscribe(subscription -> log.info("Yeni bir SSE bağlantısı oluşturuldu"))
                .doOnCancel(() -> log.info("SSE istemcisi bağlantıyı kesti"));
    }

    @GetMapping("/test")
    public void testEmit() {
        sink.tryEmitNext(new Student(1, "Test Student", 25));
    }
}
