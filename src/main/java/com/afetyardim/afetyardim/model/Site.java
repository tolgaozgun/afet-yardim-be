package com.afetyardim.afetyardim.model;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Getter
@Setter
@TypeDef(name = "json", typeClass = JsonType.class)
@Table(indexes = @Index(name = "city_index", columnList = "city"))
public class Site {

    public Site() {
        this.createDateTime = LocalDateTime.now();
    }

  @Id
  @Column(name = "ID", nullable = false, unique = true)
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @CreationTimestamp
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private LocalDateTime createDateTime;

  private String name;

  @Embedded
  private Location location;

  private String organizer;

  @Column(length = 1024)
  private String description;

  private String contactInformation;

  @Type(type = "json")
  @Column(columnDefinition = "jsonb")
  private List<SiteUpdate> updates = new ArrayList<>();

  @Type(type = "json")
  @Column(columnDefinition = "jsonb")
  private List<SiteStatus> lastSiteStatuses = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  private SiteType type = SiteType.SUPPLY;

  private boolean verified = false;

  @Column(columnDefinition = "boolean default false")
  private Boolean active = false;


  public void addSiteUpdate(SiteUpdate siteUpdate) {
    if (getUpdates() == null) {
      updates = new ArrayList<>();
    }
    updates.add(siteUpdate);

  }

  public List<SiteStatus> getLastSiteStatuses() {
    if (lastSiteStatuses == null || lastSiteStatuses.isEmpty()) {
      lastSiteStatuses = List.of(
              new SiteStatus(SiteStatusType.HUMAN_HELP, SiteStatus.SiteStatusLevel.NO_NEED_REQUIRED),
              new SiteStatus(SiteStatusType.FOOD, SiteStatus.SiteStatusLevel.NO_NEED_REQUIRED),
              new SiteStatus(SiteStatusType.MATERIAL, SiteStatus.SiteStatusLevel.NO_NEED_REQUIRED),
              new SiteStatus(SiteStatusType.PACKAGE, SiteStatus.SiteStatusLevel.NO_NEED_REQUIRED)
      );
    }
    return lastSiteStatuses;
  }


}
