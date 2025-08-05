package com.mcit.service;

import com.mcit.entity.MyUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserInfoService {
    @Autowired
    private  MyUserDetailService MyUserSer;


    public MyUser getCurrentUser(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication !=null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof MyUser){
            MyUser user=(MyUser) authentication.getPrincipal();
            return user;
        }
        return null;
    }
    public Long getCurrentUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication !=null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof MyUser){
            MyUser user=(MyUser) authentication.getPrincipal();
            return user.getId();
        }
        return null;
    }
    public String getCurrentUserFirstName(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication !=null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof MyUser){
            MyUser user = (MyUser) authentication.getPrincipal();
            return user.getFirstname();
        }
        return null;
    }
    public String getCurrentUserLastName(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication !=null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof MyUser){
            MyUser user = (MyUser) authentication.getPrincipal();
            return user.getLastname();
        }
        return null;
    }
    public String getCurrentUserEmail(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication !=null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof MyUser){
            MyUser user = (MyUser) authentication.getPrincipal();
            return user.getEmail();
        }
        return null;
    }



//    public List<Long> getCurrentUserDepartmentIds(){
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        if (authentication !=null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof MyUser){
//            MyUser user = (MyUser) authentication.getPrincipal();
//            return user.getDepartment().stream().map(department ->
//                    department.getDepId()).collect(Collectors.toList());
//        }
//        return null;
//    }

//    public List<Department> getCurrentUserDepartments(){
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        if (authentication !=null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof MyUser){
//            MyUser user = (MyUser) authentication.getPrincipal();
//            List<Department> departmentList = MyUserSer.getDepartmentsByUser(user.getId());
//            return departmentList;
//        }
//        return null;
//    }

}
