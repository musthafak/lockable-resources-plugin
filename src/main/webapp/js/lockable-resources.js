// SPDX-License-Identifier: MIT
// Copyright (c) 2020, Tobias Gruetzmacher

function find_resource_name(element) {
  var row = element.up('tr');
  var resourceName = row.getAttribute('data-resource-name');
  return resourceName;
}

function find_resource_message(element) {
  var row = element.up('tr');
  return row.querySelector("input").value;
}

function resource_action(button, action) {
  // TODO: Migrate to form:link after Jenkins 2.233 (for button-styled links)
  var form = document.createElement('form');
  form.setAttribute('method', 'POST');
  name = encodeURIComponent(find_resource_name(button));
  var url = action + "?resource=" + name;
  if (action === "reserve") {
    message = encodeURIComponent(find_resource_message(button));
    url += "&message=" + message;
  }
  form.setAttribute('action', url);
  crumb.appendToForm(form);
  document.body.appendChild(form);
  form.submit();
}

function toggle_reserve(button) {
  var row = button.up('tr');
  var action_button = row.querySelector(".action-button");
  var toggle_button = row.querySelector(".toggle-button");
  var resource_message = row.querySelector(".resource-message");
  var message_input = row.querySelector("input");
  if (action_button.style.display === "none") {
    toggle_button.innerHTML = "Cancel";
    action_button.style.display = "block";
    message_input.style.display = "block";
    resource_message.style.display = "none";
  } else {
    toggle_button.innerHTML = "Reserve";
    action_button.style.display = "none";
    message_input.style.display = "none";
    resource_message.style.display = "block";
  }
}
