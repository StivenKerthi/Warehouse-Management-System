package com.example.wms.truck.mapper;

import com.example.wms.truck.dto.TruckDto;
import com.example.wms.truck.model.Truck;
import org.mapstruct.Mapper;

@Mapper
public interface TruckMapper {

    TruckDto toDto(Truck truck);
}
