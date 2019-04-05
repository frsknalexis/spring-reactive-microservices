package com.mz.user.domain.aggregate;

import com.mz.reactivedemo.common.aggregate.AbstractRootAggregate;
import com.mz.reactivedemo.common.aggregate.Aggregate;
import com.mz.reactivedemo.common.aggregate.AggregateStatus;
import com.mz.reactivedemo.common.aggregate.Id;
import com.mz.reactivedemo.common.api.events.Command;
import com.mz.reactivedemo.common.api.events.Event;
import com.mz.reactivedemo.common.api.util.CaseMatch;
import com.mz.reactivedemo.common.api.util.Match;
import com.mz.user.domain.event.ContactInfoCreated;
import com.mz.user.domain.event.UserCreated;
import com.mz.user.dto.ContactInfoDto;
import com.mz.user.dto.UserDto;
import com.mz.user.message.command.CreateContactInfo;
import com.mz.user.message.command.CreateUser;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;

import java.time.Instant;
import java.util.Optional;

/**
 * Created by zemi on 02/01/2019.
 */
public class UserAggregate extends AbstractRootAggregate<UserDto> {

  private Id id;

  private Optional<ContactInfo> contactInformation = Optional.empty();

  private FirstName firstName;

  private LastName lastName;

  private Long version;

  private Instant createdAt;

  private UserAggregate(String id) {
    this.id = new Id(id);
    this.version = 0L;
    this.status = AggregateStatus.NEW;
  }

  private UserAggregate(UserDto userDto) {
    id = new Id(userDto.id());
    firstName = new FirstName(userDto.firstName());
    lastName = new LastName(userDto.lastName());
    version = userDto.version();
    createdAt = userDto.createdAt();

    contactInformation = userDto.contactInformation()
        .map(c ->
            ContactInfo.builder()
                .userId(id)
                .email(c.email().map(Email::new))
                .phoneNumber(c.phoneNumber().map(PhoneNumber::new))
                .createdAt(c.createdAt())
                .build());
    this.status = AggregateStatus.EXISTING;
  }

  private Event validateCreateUser(CreateUser cmd) {
    FirstName firstName = new FirstName(cmd.firstName());
    LastName lastName = new LastName(cmd.lastName());
    Optional<ContactInfo> contactInformation = cmd.contactInformation()
        .map(c -> ContactInfo.builder()
            .userId(id)
            .email(c.email().map(Email::new))
            .phoneNumber(c.phoneNumber().map(PhoneNumber::new))
            .build());
    return UserCreated.builder()
        .firstName(firstName.value)
        .lastName(lastName.value)
        .id(id.value)
        .version(version)
        .email(contactInformation.flatMap(ci -> ci.email().map(em -> em.value)))
        .phoneNumber(contactInformation.flatMap(ci -> ci.phoneNumber().map(n -> n.value)))
        .build();
  }

  private void applyUserCreated(UserCreated evt) {
    this.firstName = new FirstName(evt.firstName());
    this.lastName = new LastName(evt.lastName());
    this.createdAt = Instant.now();
    evt.email().ifPresent(e -> {
      ContactInfo contactInfo = ContactInfo.builder()
          .from(this.contactInformation.orElseGet(() -> ContactInfo.builder().userId(this.id).build()))
          .email(new Email(e))
          .build();
      this.contactInformation = Optional.of(contactInfo);
    });
    evt.phoneNumber().ifPresent(n -> {
      ContactInfo contactInfo = ContactInfo.builder()
          .from(this.contactInformation.orElseGet(() -> ContactInfo.builder().userId(this.id).build()))
          .phoneNumber(new PhoneNumber(n))
          .build();
      this.contactInformation = Optional.of(contactInfo);
    });
    status = AggregateStatus.EXISTING;
  }

  private ContactInfoCreated validateCreateContactInformation(CreateContactInfo cmd) {
    if (status == AggregateStatus.NEW) {
      throw new RuntimeException("Wrong state of aggregate");
    }
    ContactInfo.builder()
        .userId(id)
        .email(cmd.email().map(Email::new))
        .phoneNumber(cmd.phoneNumber().map(PhoneNumber::new))
        .build();
    return ContactInfoCreated.builder()
        .userId(id.value)
        .createdAt(Instant.now())
        .email(cmd.email())
        .phoneNumber(cmd.phoneNumber())
        .userVersion(version)
        .build();
  }

  private void applyContactInfoCreated(ContactInfoCreated evt) {
    this.contactInformation = Optional.ofNullable(evt)
        .map(c -> ContactInfo.builder()
            .email(c.email().map(Email::new))
            .phoneNumber(c.phoneNumber().map(PhoneNumber::new))
            .userId(new Id(c.userId()))
            .build());
    ++ version;
  }

  @Override
  protected ImmutableList<Event> behavior(Command cmd) {
    return (Match.<Event>match(cmd)
        .when(CreateUser.class, this::validateCreateUser)
        .when(CreateContactInfo.class, this::validateCreateContactInformation)
        .get().map(Lists.immutable::of).orElseGet(Lists.immutable::empty));
  }

  @Override
  protected String getRootEntityId() {
    return id.value;
  }

  @Override
  public Aggregate<UserDto> apply(Event event) {
    CaseMatch.match(event)
        .when(UserCreated.class, this::applyUserCreated)
        .when(ContactInfoCreated.class, this::applyContactInfoCreated);
    return this;
  }


  @Override
  public UserDto state() {
    return UserDto.builder()
        .firstName(this.firstName.value)
        .lastName(this.lastName.value)
        .id(this.id.value)
        .version(this.version)
        .createdAt(this.createdAt)
        .contactInformation(this.contactInformation.map(c ->
            ContactInfoDto.builder()
                .email(c.email().map(e -> e.value))
                .phoneNumber(c.phoneNumber().map(p -> p.value))
                .userId(id.value)
                .createdAt(this.createdAt)
                .build()
        ))
        .build();
  }

  public static UserAggregate of(UserDto userDto) {
    return new UserAggregate(userDto);
  }

  public static UserAggregate of(String id) {
    return new UserAggregate(id);
  }
}
