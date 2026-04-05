package com.example.wms.user.mapper;

import com.example.wms.user.dto.UserDto;
import com.example.wms.user.model.User;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for {@link User} ↔ {@link UserDto} conversion.
 * The generated implementation is a Spring bean (defaultComponentModel=spring
 * is set globally in pom.xml).
 */
@Mapper
public interface UserMapper {

    UserDto toDto(User user);
}
