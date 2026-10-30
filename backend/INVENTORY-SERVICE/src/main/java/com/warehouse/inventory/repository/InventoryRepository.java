package com.warehouse.inventory.repository;

import com.warehouse.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    List<Inventory> findByProductId(Long productId);

    List<Inventory> findByWarehouseId(Long warehouseId);

    Optional<Inventory> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    boolean existsByProductIdAndWarehouseId(Long productId, Long warehouseId);

    boolean existsByWarehouseId(Long warehouseId);

    /**
     * Acquires a row level write lock so two concurrent orders cannot
     * reserve the same units. This is the key to stock reconciliation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.productId = :productId and i.warehouseId = :warehouseId")
    Optional<Inventory> findForUpdate(@Param("productId") Long productId,
                                      @Param("warehouseId") Long warehouseId);

    @Query("select i from Inventory i where (i.quantity - i.reservedQuantity) <= i.reorderLevel")
    List<Inventory> findLowStock();

    long countByWarehouseId(Long warehouseId);
}
