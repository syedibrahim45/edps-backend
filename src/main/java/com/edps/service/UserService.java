package com.edps.service;

import com.edps.entity.User;
import com.edps.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(User user) {
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public User updateUser(Long id, User user) {
        User existingUser = userRepository.findById(id).orElse(null);

        if (existingUser == null) {
            return null;
        }

        existingUser.setUsername(user.getUsername());
        existingUser.setPassword(user.getPassword());
        existingUser.setRole(user.getRole());

        return userRepository.save(existingUser);
    }

    public boolean deleteUser(Long id) {
        User existingUser = userRepository.findById(id).orElse(null);

        if (existingUser == null) {
            return false;
        }

        userRepository.delete(existingUser);
        return true;
    }
}