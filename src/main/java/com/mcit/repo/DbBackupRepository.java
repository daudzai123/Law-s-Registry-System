package com.mcit.repo;

import com.mcit.entity.BackupDB;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DbBackupRepository extends JpaRepository<BackupDB,Long> {

}
