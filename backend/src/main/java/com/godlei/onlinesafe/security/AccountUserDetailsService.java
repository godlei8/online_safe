package com.godlei.onlinesafe.security;

import com.godlei.onlinesafe.auth.application.PhoneNormalizer;
import com.godlei.onlinesafe.auth.application.InvalidRegistrationException;
import com.godlei.onlinesafe.auth.application.UsernameNormalizer;
import com.godlei.onlinesafe.auth.domain.AppUser;
import com.godlei.onlinesafe.auth.domain.AppUserStatus;
import com.godlei.onlinesafe.auth.infrastructure.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final PhoneNormalizer phoneNormalizer;
    private final UsernameNormalizer usernameNormalizer;

    public AccountUserDetailsService(
            AppUserRepository userRepository,
            PhoneNormalizer phoneNormalizer,
            UsernameNormalizer usernameNormalizer
    ) {
        this.userRepository = userRepository;
        this.phoneNormalizer = phoneNormalizer;
        this.usernameNormalizer = usernameNormalizer;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        Optional<AppUser> user = findUser(identifier);
        AppUser found = user.orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));

        return new AppUserPrincipal(
                found.getId(),
                found.getUsername(),
                found.getPasswordHash(),
                found.getStatus() == AppUserStatus.ACTIVE
        );
    }

    private Optional<AppUser> findUser(String identifier) {
        try {
            return userRepository.findByPhone(phoneNormalizer.normalize(identifier));
        } catch (InvalidRegistrationException ignored) {
            String username = usernameNormalizer.normalizeForLogin(identifier);
            return userRepository.findByNormalizedUsername(username);
        }
    }
}
