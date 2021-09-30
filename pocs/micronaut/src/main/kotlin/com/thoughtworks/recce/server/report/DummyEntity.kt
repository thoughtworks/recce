package com.thoughtworks.recce.server.report

import javax.persistence.*

// TODO Remove me when we have real entities; required for startup
@Entity
class DummyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    open var id: Long? = null

}