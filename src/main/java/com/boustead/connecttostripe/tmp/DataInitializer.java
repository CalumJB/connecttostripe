package com.boustead.connecttostripe.tmp;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;

    public DataInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
//        if (userRepository.count() == 0) {  // Insert only if table empty
//            User newUser = new User("newuser@example.com");
//            userRepository.save(newUser);
//            System.out.println("Inserted user: " + newUser.getEmail());
//        }
    }
}
