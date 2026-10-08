// Account: who is logged in for the page header: their name and whether they are an admin.

package com.wisertech.shipviewer.web.dto;

public record Account(String name, boolean admin) {
}
