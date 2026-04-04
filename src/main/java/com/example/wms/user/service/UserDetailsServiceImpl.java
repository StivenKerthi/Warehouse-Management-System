package com.example.wms.user.service;

import com.example.wms.user.model.User;
import com.example.wms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring Security {@link UserDetailsService} backed by {@link UserRepository}.
 *
 * <p>Roles are mapped to Spring Security authorities using the standard
 * {@code ROLE_} prefix convention (e.g. {@code ROLE_WAREHOUSE_MANAGER}).
 * This lets {@code @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")} work
 * without further configuration.
 *
 * <p>Inactive users ({@code active = false}) are returned with
 * {@code enabled = false}; Spring Security's
 * {@code DaoAuthenticationProvider} will reject them with a
 * {@code DisabledException} before the password is even checked.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with username: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .disabled(!user.isActive())
                .accountLocked(false)
                .accountExpired(false)
                .credentialsExpired(false)
                .build();
    }
}
