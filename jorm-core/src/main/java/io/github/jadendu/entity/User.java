// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.entity;

import io.github.jadendu.annotation.*;
import io.github.jadendu.annotation.Enum.GenerationType;

@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_name")
    private String name;

    private int age;
    private String status;
    private String department;

    @Aggregation private int totalAge;

    public User(String name, int age, String status) {
        this.name = name;
        this.age = age;
        this.status = status;
    }

    public User() {}

    public User(String name, String status) {
        this.name = name;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getTotalAge() {
        return totalAge;
    }

    public void setTotalAge(int totalAge) {
        this.totalAge = totalAge;
    }
}
