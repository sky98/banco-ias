import { Component } from '@angular/core';
import { PreApprovedRequestsPage } from './features/preapproved-requests/preapproved-requests.page';

@Component({
  imports: [PreApprovedRequestsPage],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {}
