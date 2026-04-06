package com.example.wms.inventory.mapper;

import com.example.wms.inventory.dto.InventoryItemDto;
import com.example.wms.inventory.model.InventoryItem;
import org.mapstruct.Mapper;

@Mapper
public interface InventoryItemMapper {

    InventoryItemDto toDto(InventoryItem item);
}
