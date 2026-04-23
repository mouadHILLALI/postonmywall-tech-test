package com.postonmywall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PostonmywallApplication {

	public static void main(String[] args) {
		SpringApplication.run(PostonmywallApplication.class, args);
	}

}
