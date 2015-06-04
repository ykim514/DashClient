function activeSideMenu(menuIndex) {
  var list = $('#navbar li');
  for (var i = 0; i < list.length; i++) {
    if (i == menuIndex) {
      $(list[i]).attr('class', 'active');
    } else {
      $(list[i]).removeAttr('class');
    }
  }
}
